package com.monotron.support.channels;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.ai.AnswerText;
import com.monotron.support.ai.RagService;
import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.realtime.RealtimePublisher;
import com.monotron.support.security.TenantBinding;
import com.monotron.support.security.TenantContext;
import com.monotron.support.tenancy.AppService;
import com.monotron.support.tenancy.dto.AppDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles Meta webhook deliveries for WhatsApp Cloud API and Instagram
 * Messaging.
 *
 * <p>Meta sends one callback for potentially several messages, and retries a
 * delivery when the endpoint is slow. The HTTP response is therefore returned
 * immediately and the payload is processed asynchronously. Every message is
 * committed in its own tenant-bound transaction and its provider message id is
 * stored in {@code channel_inbound_messages}, so retries cannot create
 * duplicates or send the same AI answer twice.
 */
@Service
public class MetaWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookService.class);
    private static final int MAX_BODY_LENGTH = 20_000;
    private static final TypeReference<Map<String, String>> METADATA_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final AppService appService;
    private final RagService ragService;
    private final MetaApiClient meta;
    private final ChannelCredentialCrypto crypto;
    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final TenantBinding tenantBinding;
    private final TransactionTemplate messageTransaction;

    public MetaWebhookService(JdbcTemplate jdbc,
                              AppService appService,
                              RagService ragService,
                              MetaApiClient meta,
                              ChannelCredentialCrypto crypto,
                              RealtimePublisher realtime,
                              ObjectMapper mapper,
                              AppProperties properties,
                              TenantBinding tenantBinding,
                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.appService = appService;
        this.ragService = ragService;
        this.meta = meta;
        this.crypto = crypto;
        this.realtime = realtime;
        this.mapper = mapper;
        this.properties = properties;
        this.tenantBinding = tenantBinding;
        this.messageTransaction = new TransactionTemplate(transactionManager);
        this.messageTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Verifies Meta's {@code X-Hub-Signature-256} header against the exact
     * request bytes.
     */
    public boolean validSignature(byte[] body, String signature) {
        AppProperties.Channels.Meta metaProperties = properties.getChannels().getMeta();
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(signature.substring("sha256=".length()).trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        // WhatsApp webhooks are signed with the Meta app secret. Instagram
        // webhooks use the dedicated Instagram product secret. Both belong to
        // the same Meta app, so a signature matching either is authentic.
        return matchesSecret(body, actual, metaProperties.getAppSecret())
                || matchesSecret(body, actual, metaProperties.getInstagramAppSecret());
    }

    private boolean matchesSecret(byte[] body, byte[] actual, String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body == null ? new byte[0] : body);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            log.warn("Could not verify a Meta webhook signature", ex);
            return false;
        }
    }

    public boolean validVerifyToken(String token) {
        AppProperties.Channels.Meta metaProperties = properties.getChannels().getMeta();
        if (!metaProperties.isWebhookConfigured() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                metaProperties.getWebhookVerifyToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Processes a verified webhook without delaying Meta's delivery response.
     */
    @Async
    public void processAsync(byte[] rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            if (root.isArray()) {
                for (JsonNode child : root) {
                    processRoot(child);
                }
            } else {
                processRoot(root);
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Could not process a Meta webhook", ex);
        }
    }

    private void processRoot(JsonNode root) {
        String object = text(root, "object");
        if ("whatsapp_business_account".equals(object)) {
            processWhatsApp(root);
        } else if ("instagram".equals(object)) {
            processInstagram(root);
        } else {
            log.debug("Ignoring Meta webhook object {}", object);
        }
    }

    private void processWhatsApp(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                if (!"messages".equals(text(change, "field"))) {
                    continue;
                }
                JsonNode value = change.path("value");
                String phoneNumberId = text(value.path("metadata"), "phone_number_id");
                if (isBlank(phoneNumberId)) {
                    continue;
                }
                Map<String, String> contactNames = whatsappContactNames(value.path("contacts"));
                for (JsonNode message : value.path("messages")) {
                    String messageId = text(message, "id");
                    String senderId = firstNonBlank(
                            text(message, "from"), text(message, "from_user_id"));
                    if (isBlank(messageId) || isBlank(senderId)) {
                        continue;
                    }
                    String type = text(message, "type");
                    if ("reaction".equals(type)) {
                        continue;
                    }
                    MessageContent content = whatsappContent(message, type);
                    if (content.body() == null) {
                        continue;
                    }
                    processMessage(new MessageEvent(
                            "WHATSAPP",
                            phoneNumberId,
                            messageId,
                            senderId,
                            contactNames.getOrDefault(senderId, "WhatsApp user"),
                            content.body(),
                            content.replyEligible()));
                }
            }
        }
    }

    private void processInstagram(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            String entryId = text(entry, "id");
            for (JsonNode event : entry.path("messaging")) {
                JsonNode message = event.path("message");
                if (!message.isObject()
                        || message.path("is_echo").asBoolean(false)
                        || message.path("is_deleted").asBoolean(false)) {
                    continue;
                }
                String senderId = text(event.path("sender"), "id");
                String recipientId = text(event.path("recipient"), "id");
                String accountId = isBlank(recipientId) ? entryId : recipientId;
                String messageId = text(message, "mid");
                if (isBlank(senderId) || isBlank(accountId) || isBlank(messageId)) {
                    continue;
                }
                MessageContent content = instagramContent(message);
                if (content.body() == null) {
                    continue;
                }
                processMessage(new MessageEvent(
                        "INSTAGRAM",
                        accountId,
                        messageId,
                        senderId,
                        "Instagram user",
                        content.body(),
                        content.replyEligible()));
            }
        }
    }

    private void processMessage(MessageEvent event) {
        Optional<String> workspaceId = lookupWorkspace(event.channel(), event.accountId());
        if (workspaceId.isEmpty()) {
            log.info("No workspace is connected for {} account {}", event.channel(), event.accountId());
            return;
        }
        try {
            Boolean imported = inTenant(workspaceId.get(), () -> importOne(event));
            if (Boolean.TRUE.equals(imported)) {
                log.debug("Imported {} message {}", event.channel(), event.providerMessageId());
            }
        } catch (BadRequestException ex) {
            log.warn("{} message import skipped for workspace {}: {}",
                    event.channel(), workspaceId.get(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Could not import {} message {} for workspace {}",
                    event.channel(), event.providerMessageId(), workspaceId.get(), ex);
        }
    }

    private boolean importOne(MessageEvent event) {
        if (alreadyImported(event.channel(), event.providerMessageId())) {
            return false;
        }

        Credential credential = credential(event.channel());
        if (credential == null || !matchesAccount(credential, event.accountId())) {
            log.warn("No matching {} credential for account {}", event.channel(), event.accountId());
            return false;
        }

        // Importing is independent from automation: a connected channel should
        // not lose a customer message just because auto-reply is paused.
        Automation automation = automation(event.channel());
        if (automation == null) {
            automation = new Automation(false, false);
        }

        AppDto app = appService.findCurrent().orElse(null);
        if (app == null) {
            return false;
        }

        String accessToken = accessToken(event.channel(), credential);
        Map<String, String> metadata = metadata(credential.metadataJson());
        ContactRow contact = resolveContact(app.id(), event);
        ConversationRow conversation = resolveConversation(app.id(), contact.id(), event);

        insertContactMessage(conversation.id(), contact.name(), event.body());
        touchConversation(conversation.id());
        insertInboundMarker(event.channel(), event.providerMessageId(), conversation.id());

        String workspaceId = TenantContext.get();
        if (automation.enabled() && automation.autoReply() && app.aiEnabled()
                && !conversation.escalated() && event.replyEligible()) {
            String answer = answer(app, event.body());
            insertAiMessage(conversation.id(), app.name(), answer);
            markAiHandled(conversation.id());
            afterCommit(() -> sendReplyQuietly(event, metadata, accessToken, answer));
        }

        afterCommit(() -> {
            if (workspaceId != null) {
                try {
                    realtime.messageCreated(workspaceId, conversation.id(), false);
                } catch (RuntimeException ex) {
                    log.debug("Could not publish the {} message event", event.channel(), ex);
                }
            }
        });
        return true;
    }

    private Credential credential(String channel) {
        List<Credential> rows = jdbc.query("""
                select external_account_id, encrypted_access_token, token_expires_at, metadata::text
                from channel_credentials
                where channel = ?
                """, (rs, i) -> new Credential(
                rs.getString("external_account_id"),
                rs.getString("encrypted_access_token"),
                rs.getTimestamp("token_expires_at") == null
                        ? null
                        : rs.getTimestamp("token_expires_at").toInstant(),
                rs.getString("metadata")), channel);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean matchesAccount(Credential credential, String accountId) {
        if (accountId != null && accountId.equals(credential.externalAccountId())) {
            return true;
        }
        Map<String, String> metadata = metadata(credential.metadataJson());
        return accountId != null
                && (accountId.equals(metadata.get("pageId"))
                || accountId.equals(metadata.get("igUserId")));
    }

    private String accessToken(String channel, Credential credential) {
        String accessToken = crypto.decrypt(credential.encryptedAccessToken());
        if (isBlank(accessToken)) {
            throw new BadRequestException("The channel credential is missing. Reconnect the channel.");
        }
        Map<String, String> metadata = metadata(credential.metadataJson());
        boolean unifiedInstagram = "INSTAGRAM".equals(channel)
                && "true".equalsIgnoreCase(metadata.get("unifiedLogin"));
        // WhatsApp long-lived user tokens use fb_exchange_token. Legacy
        // Instagram Login tokens use the Instagram refresh endpoint. Unified
        // Login tokens are business integration system user tokens that do not
        // use that endpoint.
        if (credential.expiresAt() != null
                && credential.expiresAt().isBefore(Instant.now().plus(Duration.ofDays(1)))) {
            if (unifiedInstagram) {
                throw new BadRequestException(
                        "The Instagram connection expired. Reconnect Instagram "
                                + "to receive messages again.");
            }
            MetaApiClient.MetaToken refreshed;
            try {
                refreshed = "INSTAGRAM".equals(channel)
                        ? meta.refreshInstagramToken(accessToken)
                        : meta.refreshUserToken(accessToken);
            } catch (RuntimeException ex) {
                throw new BadRequestException(
                        "The Meta connection expired. Reconnect " + channel
                                + " to receive messages again.");
            }
            jdbc.update("""
                    update channel_credentials
                    set encrypted_access_token = ?, token_expires_at = ?, updated_at = now()
                    where channel = ?
                    """, crypto.encrypt(refreshed.accessToken()),
                    refreshed.expiresAt() == null ? null : Timestamp.from(refreshed.expiresAt()),
                    channel);
            return refreshed.accessToken();
        }
        return accessToken;
    }

    private Automation automation(String channel) {
        List<Automation> rows = jdbc.query("""
                select enabled, auto_reply
                from channel_automations
                where channel = ?
                """, (rs, i) -> new Automation(rs.getBoolean("enabled"), rs.getBoolean("auto_reply")),
                channel);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ContactRow resolveContact(String appId, MessageEvent event) {
        String externalKey = event.channel() + ":" + event.senderId();
        List<ContactRow> existing = jdbc.query("""
                select id, name from contacts
                where app_id = ? and visitor_id = ?
                limit 1
                """, (rs, i) -> new ContactRow(rs.getString("id"), rs.getString("name")),
                appId, externalKey);
        if (!existing.isEmpty()) {
            ContactRow contact = existing.get(0);
            String updateName = "WHATSAPP".equals(event.channel()) ? event.senderName() : null;
            String name = isBlank(updateName) ? contact.name() : updateName;
            jdbc.update("""
                    update contacts
                    set name = ?, online = true, last_seen_at = now(), updated_at = now()
                    where id = ?
                    """, name, contact.id());
            return new ContactRow(contact.id(), isBlank(name) ? event.channel() + " user" : name);
        }

        String contactId = Ids.uuid();
        String name = isBlank(event.senderName()) ? event.channel() + " user" : event.senderName();
        jdbc.update("""
                insert into contacts (id, app_id, visitor_id, name, online, last_seen_at,
                                      custom_attributes, created_at, updated_at)
                values (?, ?, ?, ?, true, now(), ?::jsonb, now(), now())
                """, contactId, appId, externalKey, truncate(name, 255),
                attributes(event.channel(), event.senderId()));
        return new ContactRow(contactId, truncate(name, 255));
    }

    private ConversationRow resolveConversation(String appId, String contactId, MessageEvent event) {
        List<ConversationRow> existing = jdbc.query("""
                select id, escalated from conversations
                where app_id = ? and channel = ? and external_thread_id = ?
                limit 1
                """, (rs, i) -> new ConversationRow(rs.getString("id"), rs.getBoolean("escalated")),
                appId, event.channel(), event.senderId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String conversationId = Ids.uuid();
        jdbc.update("""
                insert into conversations (id, app_id, contact_id, subject, status, channel,
                                          external_thread_id, ai_handled, unread_count, escalated,
                                          created_at, updated_at, last_activity_at)
                values (?, ?, ?, ?, 'OPEN', ?, ?, false, 0, false, now(), now(), now())
                on conflict do nothing
                """, conversationId, appId, contactId, subject(event), event.channel(), event.senderId());
        List<ConversationRow> created = jdbc.query("""
                select id, escalated from conversations
                where app_id = ? and channel = ? and external_thread_id = ?
                limit 1
                """, (rs, i) -> new ConversationRow(rs.getString("id"), rs.getBoolean("escalated")),
                appId, event.channel(), event.senderId());
        return created.isEmpty() ? new ConversationRow(conversationId, false) : created.get(0);
    }

    private void insertContactMessage(String conversationId, String authorName, String body) {
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'CONTACT', ?, ?, now(), '[]'::jsonb)
                """, Ids.uuid(), conversationId, authorName, truncate(body, MAX_BODY_LENGTH));
    }

    private void touchConversation(String conversationId) {
        jdbc.update("""
                update conversations
                set status = 'OPEN', closed_at = null, last_activity_at = now(),
                    unread_count = unread_count + 1, updated_at = now()
                where id = ?
                """, conversationId);
    }

    private void insertInboundMarker(String channel, String providerMessageId, String conversationId) {
        jdbc.update("""
                insert into channel_inbound_messages (id, channel, provider_message_id, conversation_id)
                values (?, ?, ?, ?)
                """, Ids.uuid(), channel, providerMessageId, conversationId);
    }

    private boolean alreadyImported(String channel, String providerMessageId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from channel_inbound_messages
                where channel = ? and provider_message_id = ?
                """, Integer.class, channel, providerMessageId);
        return count != null && count > 0;
    }

    private String answer(AppDto app, String question) {
        AnswerDto answer = ragService.answer(question, app.aiSystemPrompt(), app.aiTemperature());
        String body = AnswerText.forAiMessage(answer.answer(), answer.citations());
        return isBlank(body) ? "Thanks for your message. A teammate will follow up shortly." : body;
    }

    private void insertAiMessage(String conversationId, String authorName, String body) {
        jdbc.update("""
                insert into messages (id, conversation_id, author_type, author_name, body, created_at, citations)
                values (?, ?, 'AI', ?, ?, now(), ?::jsonb)
                """, Ids.uuid(), conversationId, authorName, body, writeCitations());
    }

    private void markAiHandled(String conversationId) {
        jdbc.update("""
                update conversations
                set ai_handled = true, unread_count = 0, last_activity_at = now(), updated_at = now()
                where id = ?
                """, conversationId);
    }

    private void sendReplyQuietly(MessageEvent event, Map<String, String> metadata,
                                  String accessToken, String body) {
        try {
            if ("WHATSAPP".equals(event.channel())) {
                String phoneNumberId = metadata.get("phoneNumberId");
                if (isBlank(phoneNumberId)) {
                    throw new IllegalStateException("The WhatsApp phone number id is missing.");
                }
                meta.sendWhatsAppText(phoneNumberId, accessToken, event.senderId(),
                        truncate(body, 4_096));
            } else {
                String igUserId = metadata.get("igUserId");
                if (isBlank(igUserId)) {
                    throw new IllegalStateException("The Instagram account id is missing.");
                }
                if ("true".equalsIgnoreCase(metadata.get("unifiedLogin"))) {
                    meta.sendInstagramUnifiedText(igUserId, accessToken, event.senderId(),
                            truncate(body, 1_000));
                } else {
                    meta.sendInstagramText(accessToken, event.senderId(),
                            truncate(body, 1_000));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not send the {} reply to {}: {}",
                    event.channel(), event.senderId(), ex.getMessage());
        }
    }

    private Optional<String> lookupWorkspace(String channel, String accountId) {
        if (isBlank(accountId)) {
            return Optional.empty();
        }
        try {
            return jdbc.query("""
                    select workspace_id from channel_account_lookup
                    where channel = ? and external_account_id = ?
                    limit 1
                    """, rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                    channel, accountId);
        } catch (RuntimeException ex) {
            log.warn("Could not resolve a workspace for {} account {}", channel, accountId, ex);
            return Optional.empty();
        }
    }

    private boolean inTenant(String workspaceId, Supplier<Boolean> action) {
        return Boolean.TRUE.equals(messageTransaction.execute(status -> {
            String previous = TenantContext.get();
            tenantBinding.bind(workspaceId);
            try {
                return action.get();
            } finally {
                if (previous != null) {
                    TenantContext.set(previous);
                } else {
                    TenantContext.clear();
                }
            }
        }));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private Map<String, String> metadata(String json) {
        if (isBlank(json)) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("Could not read channel metadata", ex);
            return Map.of();
        }
    }

    private String writeCitations() {
        return "[]";
    }

    private String attributes(String channel, String externalId) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("channel", channel);
        values.put("external_id", externalId);
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static Map<String, String> whatsappContactNames(JsonNode contacts) {
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode contact : contacts) {
            String name = text(contact.path("profile"), "name");
            if (isBlank(name)) {
                continue;
            }
            String waId = text(contact, "wa_id");
            if (!isBlank(waId)) {
                names.put(waId, name);
            }
            String userId = text(contact, "user_id");
            if (!isBlank(userId)) {
                names.put(userId, name);
            }
        }
        return names;
    }

    private static MessageContent whatsappContent(JsonNode message, String type) {
        if (type == null) {
            return new MessageContent(null, false);
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "text" -> textContent(text(message.path("text"), "body"));
            case "button" -> textContent(text(message.path("button"), "text"));
            case "interactive" -> {
                JsonNode interactive = message.path("interactive");
                String value = text(interactive.path("button_reply"), "title");
                if (isBlank(value)) {
                    value = text(interactive.path("list_reply"), "title");
                }
                yield textContent(value);
            }
            case "location" -> new MessageContent("[Location shared]", false);
            case "contacts" -> new MessageContent("[Contact card shared]", false);
            case "image", "video", "document", "audio" -> {
                String caption = text(message.path(type), "caption");
                yield isBlank(caption)
                        ? new MessageContent("[" + capitalise(type) + " received]", false)
                        : textContent(caption);
            }
            default -> new MessageContent("[" + capitalise(type) + " message received]", false);
        };
    }

    private static MessageContent instagramContent(JsonNode message) {
        String value = text(message, "text");
        if (!isBlank(value)) {
            return textContent(value);
        }
        JsonNode quickReply = message.path("quick_reply");
        String quickReplyPayload = text(quickReply, "payload");
        if (!isBlank(quickReplyPayload)) {
            return textContent(quickReplyPayload);
        }
        JsonNode attachments = message.path("attachments");
        if (attachments.isArray() && !attachments.isEmpty()) {
            String type = text(attachments.get(0), "type");
            return new MessageContent("[" + capitalise(isBlank(type) ? "attachment" : type)
                    + " received]", false);
        }
        if (message.path("is_unsupported").asBoolean(false)) {
            return new MessageContent("[Unsupported Instagram message]", false);
        }
        return new MessageContent(null, false);
    }

    private static MessageContent textContent(String value) {
        return isBlank(value) ? new MessageContent(null, false) : new MessageContent(value.strip(), true);
    }

    private static String subject(MessageEvent event) {
        String channel = "WHATSAPP".equals(event.channel()) ? "WhatsApp" : "Instagram";
        return truncate(channel + " conversation with " + event.senderName(), 500);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String capitalise(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private record MessageEvent(
            String channel,
            String accountId,
            String providerMessageId,
            String senderId,
            String senderName,
            String body,
            boolean replyEligible) {
    }

    private record MessageContent(String body, boolean replyEligible) {
    }

    private record Credential(
            String externalAccountId,
            String encryptedAccessToken,
            Instant expiresAt,
            String metadataJson) {
    }

    private record Automation(boolean enabled, boolean autoReply) {
    }

    private record ContactRow(String id, String name) {
    }

    private record ConversationRow(String id, boolean escalated) {
    }
}
