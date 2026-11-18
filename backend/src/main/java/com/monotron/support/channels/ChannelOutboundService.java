package com.monotron.support.channels;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.common.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Delivers replies written by a human agent in the shared inbox back to the
 * customer's original channel.
 *
 * <p>Widget conversations are intentionally internal: the widget receives them
 * through its own realtime channel. Gmail, WhatsApp and Instagram conversations
 * are delivered through the same provider clients used by the auto-reply paths.
 *
 * <p>The caller runs inside the tenant-bound inbox transaction. Provider side
 * effects happen before that transaction commits, so a failed send makes the
 * inbox request fail and rolls back the stored agent message instead of leaving
 * a message that the customer never received.
 */
@Service
public class ChannelOutboundService {

    private static final Logger log = LoggerFactory.getLogger(ChannelOutboundService.class);
    private static final TypeReference<Map<String, String>> METADATA_TYPE =
            new TypeReference<>() {
            };
    private static final int WHATSAPP_MAX_LENGTH = 4_096;
    private static final int INSTAGRAM_MAX_LENGTH = 1_000;

    private final JdbcTemplate jdbc;
    private final ChannelCredentialCrypto crypto;
    private final GmailApiClient gmail;
    private final MetaApiClient meta;
    private final ObjectMapper mapper;

    public ChannelOutboundService(JdbcTemplate jdbc,
                                  ChannelCredentialCrypto crypto,
                                  GmailApiClient gmail,
                                  MetaApiClient meta,
                                  ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.gmail = gmail;
        this.meta = meta;
        this.mapper = mapper;
    }

    /**
     * Sends one agent-authored message to the conversation's external channel.
     * Widget conversations are a no-op because their realtime channel already
     * delivers the message.
     */
    public void deliver(String conversationId, String body) {
        ConversationTarget target = target(conversationId);
        if (target == null || target.channel() == null) {
            return;
        }
        switch (target.channel().toUpperCase(Locale.ROOT)) {
            case "GMAIL" -> sendGmail(target, body);
            case "WHATSAPP" -> sendWhatsApp(target, body);
            case "INSTAGRAM" -> sendInstagram(target, body);
            default -> {
                // WIDGET and any future internal-only channel.
            }
        }
    }

    private ConversationTarget target(String conversationId) {
        List<ConversationTarget> rows = jdbc.query("""
                select c.id, c.channel, c.external_thread_id, c.subject,
                       ct.email, ct.visitor_id
                from conversations c
                join contacts ct on ct.id = c.contact_id
                where c.id = ?
                """, (rs, i) -> new ConversationTarget(
                rs.getString("id"),
                rs.getString("channel"),
                rs.getString("external_thread_id"),
                rs.getString("subject"),
                rs.getString("email"),
                rs.getString("visitor_id")), conversationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------
    // Gmail
    // ------------------------------------------------------------------

    private void sendGmail(ConversationTarget target, String body) {
        String recipient = clean(target.contactEmail());
        if (recipient == null) {
            throw new BadRequestException(
                    "This Gmail conversation has no customer email address. Reconnect the channel.");
        }

        GmailCredential credential = gmailCredential();
        if (credential.scopes() == null
                || !credential.scopes().contains("https://www.googleapis.com/auth/gmail.send")) {
            throw new BadRequestException(
                    "Gmail is connected without permission to send email. "
                            + "Reconnect Gmail and allow sending email.");
        }

        String accessToken = gmailAccessToken(credential);
        String fromEmail = clean(credential.accountLabel());
        if (fromEmail == null || !fromEmail.contains("@")) {
            fromEmail = clean(gmail.profileEmail(accessToken));
        }
        if (fromEmail == null) {
            throw new BadRequestException(
                    "The connected Gmail account has no email address. Reconnect Gmail.");
        }

        try {
            gmail.sendReply(
                    accessToken,
                    fromEmail,
                    recipient,
                    replySubject(target.subject()),
                    null,
                    null,
                    target.externalThreadId(),
                    body);
        } catch (RuntimeException ex) {
            log.warn("Gmail rejected an agent reply for conversation {}", target.id(), ex);
            throw new BadRequestException(
                    "Gmail rejected the reply. Reconnect Gmail and try again.");
        }
    }

    private GmailCredential gmailCredential() {
        List<GmailCredential> rows = jdbc.query("""
                select account_label, encrypted_access_token, encrypted_refresh_token,
                       token_expires_at, scopes
                from channel_credentials
                where channel = 'GMAIL'
                """, (rs, i) -> new GmailCredential(
                rs.getString("account_label"),
                rs.getString("encrypted_access_token"),
                rs.getString("encrypted_refresh_token"),
                rs.getTimestamp("token_expires_at") == null
                        ? null
                        : rs.getTimestamp("token_expires_at").toInstant(),
                rs.getString("scopes")));
        if (rows.isEmpty()) {
            throw new BadRequestException(
                    "Gmail is not connected. Connect Gmail before replying to email conversations.");
        }
        return rows.get(0);
    }

    private String gmailAccessToken(GmailCredential credential) {
        String accessToken = crypto.decrypt(credential.encryptedAccessToken());
        if (accessToken != null && !accessToken.isBlank()
                && (credential.expiresAt() == null
                || credential.expiresAt().isAfter(Instant.now().plusSeconds(120)))) {
            return accessToken;
        }

        String refreshToken = crypto.decrypt(credential.encryptedRefreshToken());
        if (refreshToken == null || refreshToken.isBlank()) {
            if (accessToken != null && !accessToken.isBlank() && credential.expiresAt() == null) {
                return accessToken;
            }
            throw new BadRequestException(
                    "The Gmail connection has expired. Reconnect Gmail before replying.");
        }

        GmailApiClient.Token refreshed;
        try {
            refreshed = gmail.refresh(refreshToken);
        } catch (RuntimeException ex) {
            throw new BadRequestException(
                    "Gmail could not refresh the connection. Reconnect Gmail and try again.");
        }
        jdbc.update("""
                update channel_credentials
                set encrypted_access_token = ?, token_expires_at = ?, updated_at = now()
                where channel = 'GMAIL'
                """, crypto.encrypt(refreshed.accessToken()),
                refreshed.expiresAt() == null ? null : Timestamp.from(refreshed.expiresAt()));
        return refreshed.accessToken();
    }

    // ------------------------------------------------------------------
    // WhatsApp / Instagram
    // ------------------------------------------------------------------

    private void sendWhatsApp(ConversationTarget target, String body) {
        String recipient = externalRecipient(target, "WHATSAPP");
        MetaCredential credential = metaCredential("WHATSAPP");
        Map<String, String> metadata = metadata(credential.metadataJson());
        String phoneNumberId = clean(metadata.get("phoneNumberId"));
        if (phoneNumberId == null) {
            throw new BadRequestException(
                    "The WhatsApp connection is missing its phone number id. Reconnect WhatsApp.");
        }

        String accessToken = metaAccessToken(credential, "WHATSAPP");
        try {
            meta.sendWhatsAppText(phoneNumberId, accessToken, recipient,
                    truncate(body, WHATSAPP_MAX_LENGTH));
        } catch (RuntimeException ex) {
            log.warn("WhatsApp rejected an agent reply for conversation {}", target.id(), ex);
            throw new BadRequestException(
                    "WhatsApp rejected the reply. The customer may be outside the "
                            + "24-hour reply window, or the connection needs reconnecting.");
        }
    }

    private void sendInstagram(ConversationTarget target, String body) {
        String recipient = externalRecipient(target, "INSTAGRAM");
        MetaCredential credential = metaCredential("INSTAGRAM");
        Map<String, String> metadata = metadata(credential.metadataJson());
        String igUserId = clean(metadata.get("igUserId"));
        if (igUserId == null) {
            throw new BadRequestException(
                    "The Instagram connection is missing its account id. Reconnect Instagram.");
        }

        String accessToken = metaAccessToken(credential, "INSTAGRAM");
        try {
            if ("true".equalsIgnoreCase(metadata.get("unifiedLogin"))) {
                meta.sendInstagramUnifiedText(igUserId, accessToken, recipient,
                        truncate(body, INSTAGRAM_MAX_LENGTH));
            } else {
                meta.sendInstagramText(accessToken, recipient,
                        truncate(body, INSTAGRAM_MAX_LENGTH));
            }
        } catch (RuntimeException ex) {
            log.warn("Instagram rejected an agent reply for conversation {}", target.id(), ex);
            throw new BadRequestException(
                    "Instagram rejected the reply. The customer may be outside the "
                            + "24-hour reply window, or the connection needs reconnecting.");
        }
    }

    private String externalRecipient(ConversationTarget target, String channel) {
        String recipient = clean(target.externalThreadId());
        if (recipient == null) {
            String visitorId = clean(target.visitorId());
            String prefix = channel + ":";
            if (visitorId != null && visitorId.startsWith(prefix)) {
                recipient = clean(visitorId.substring(prefix.length()));
            }
        }
        if (recipient == null) {
            throw new BadRequestException(
                    "This conversation is missing the customer's " + channel
                            + " id. Reconnect the channel and try again.");
        }
        return recipient;
    }

    private MetaCredential metaCredential(String channel) {
        List<MetaCredential> rows = jdbc.query("""
                select encrypted_access_token, token_expires_at, metadata::text
                from channel_credentials
                where channel = ?
                """, (rs, i) -> new MetaCredential(
                rs.getString("encrypted_access_token"),
                rs.getTimestamp("token_expires_at") == null
                        ? null
                        : rs.getTimestamp("token_expires_at").toInstant(),
                rs.getString("metadata")), channel);
        if (rows.isEmpty()) {
            throw new BadRequestException(
                    channel + " is not connected. Connect " + channel
                            + " before replying to its conversations.");
        }
        return rows.get(0);
    }

    private String metaAccessToken(MetaCredential credential, String channel) {
        String accessToken = crypto.decrypt(credential.encryptedAccessToken());
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException(
                    "The " + channel + " connection is missing its access token. Reconnect it.");
        }
        if (credential.expiresAt() != null
                && credential.expiresAt().isBefore(Instant.now().plus(Duration.ofDays(1)))) {
            Map<String, String> metadata = metadata(credential.metadataJson());
            if ("INSTAGRAM".equals(channel)
                    && "true".equalsIgnoreCase(metadata.get("unifiedLogin"))) {
                throw new BadRequestException(
                        "The Instagram connection has expired. Reconnect it before replying.");
            }
            MetaApiClient.MetaToken refreshed;
            try {
                refreshed = "INSTAGRAM".equals(channel)
                        ? meta.refreshInstagramToken(accessToken)
                        : meta.refreshUserToken(accessToken);
            } catch (RuntimeException ex) {
                throw new BadRequestException(
                        "The " + channel + " connection has expired. Reconnect it before replying.");
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

    private Map<String, String> metadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, METADATA_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("Could not read channel metadata for outbound reply", ex);
            return Map.of();
        }
    }

    private static String replySubject(String subject) {
        String value = subject == null || subject.isBlank() ? "your message" : subject.strip();
        return value.toLowerCase(Locale.ROOT).startsWith("re:") ? value : "Re: " + value;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ConversationTarget(
            String id,
            String channel,
            String externalThreadId,
            String subject,
            String contactEmail,
            String visitorId) {
    }

    private record GmailCredential(
            String accountLabel,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant expiresAt,
            String scopes) {
    }

    private record MetaCredential(
            String encryptedAccessToken,
            Instant expiresAt,
            String metadataJson) {
    }
}
