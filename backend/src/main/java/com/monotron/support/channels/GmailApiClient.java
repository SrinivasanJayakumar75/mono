package com.monotron.support.channels;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.monotron.support.config.AppProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Minimal Gmail REST client built on the documented HTTPS API.
 *
 * <p>Only the tokens for the current tenant are passed in; the client itself is
 * stateless and never caches them.
 */
@Component
public class GmailApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final Pattern SENDER = Pattern.compile("(?i)^\\s*(.*?)\\s*<([^>]+)>\\s*$");
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private final AppProperties properties;
    private final RestClient restClient;

    public GmailApiClient(AppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public Token refresh(String refreshToken) {
        AppProperties.Channels.Gmail gmail = properties.getChannels().getGmail();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", gmail.getClientId());
        form.add("client_secret", gmail.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        try {
            Map<String, Object> body = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            Map<String, Object> token = body == null ? Map.of() : body;
            String accessToken = string(token.get("access_token"));
            if (accessToken == null) {
                throw new IllegalStateException("Google did not return a refreshed access token");
            }
            return new Token(accessToken, expiry(token.get("expires_in")));
        } catch (RestClientException ex) {
            throw new IllegalStateException("Google rejected the Gmail refresh token", ex);
        }
    }

    public String profileEmail(String accessToken) {
        Map<String, Object> body = get(accessToken, "/gmail/v1/users/me/profile", Map.of());
        return string(body.get("emailAddress"));
    }

    public List<MessageRef> unreadMessages(String accessToken, int maxResults) {
        Map<String, Object> body = get(accessToken, "/gmail/v1/users/me/messages", Map.of(
                "q", "is:unread -in:chats",
                "maxResults", Math.max(1, Math.min(maxResults, 50))));
        Object raw = body.get("messages");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MessageRef> refs = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String id = string(map.get("id"));
                if (id != null) {
                    refs.add(new MessageRef(id, string(map.get("threadId"))));
                }
            }
        }
        return refs;
    }

    public GmailMessage message(String accessToken, String messageId) {
        Map<String, Object> body = get(accessToken, "/gmail/v1/users/me/messages/" + messageId,
                Map.of("format", "full"));
        Map<String, Object> payload = map(body.get("payload"));
        Map<String, String> headers = headers(payload);
        String from = header(headers, "From");
        Sender sender = parseSender(from);
        return new GmailMessage(
                messageId,
                string(body.get("threadId")),
                sender.email(),
                sender.name(),
                header(headers, "Subject"),
                extractBody(payload, string(body.get("snippet"))),
                header(headers, "Message-ID"),
                header(headers, "References"),
                header(headers, "In-Reply-To"));
    }

    public void markRead(String accessToken, String messageId) {
        post(accessToken, "/gmail/v1/users/me/messages/" + messageId + "/modify",
                Map.of("removeLabelIds", List.of("UNREAD")));
    }

    public void sendReply(String accessToken, String fromEmail, String toEmail, String subject,
                          String inReplyTo, String references, String threadId, String body) {
        try {
            String raw = rawMessage(fromEmail, toEmail, subject, inReplyTo, references, body);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("raw", raw);
            if (threadId != null && !threadId.isBlank()) {
                payload.put("threadId", threadId);
            }
            post(accessToken, "/gmail/v1/users/me/messages/send", payload);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Gmail rejected the reply", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build the Gmail reply", ex);
        }
    }

    private Map<String, Object> get(String accessToken, String path, Map<String, ?> query) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host("gmail.googleapis.com").path(path);
                        query.forEach((key, value) -> uriBuilder.queryParam(key, value));
                        return uriBuilder.build();
                    })
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Gmail API request failed", ex);
        }
    }

    private Map<String, Object> post(String accessToken, String path, Object payload) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(uriBuilder -> uriBuilder.scheme("https").host("gmail.googleapis.com").path(path).build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);
            return body == null ? Map.of() : body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Gmail API request failed", ex);
        }
    }

    static String rawMessage(String fromEmail, String toEmail, String subject, String inReplyTo,
                                     String references, String body) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(new InternetAddress(fromEmail));
        }
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject == null || subject.isBlank() ? "Re: your message" : subject, "UTF-8");
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            message.setHeader("In-Reply-To", inReplyTo);
        }
        if (references != null && !references.isBlank()) {
            message.setHeader("References", references);
        }
        message.setText(body == null ? "" : body, "UTF-8");
        message.saveChanges();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    static Map<String, String> headers(Map<String, Object> payload) {
        Map<String, String> headers = new LinkedHashMap<>();
        Object raw = payload.get("headers");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String name = string(map.get("name"));
                    String value = string(map.get("value"));
                    if (name != null && value != null) {
                        headers.put(name.toLowerCase(Locale.ROOT), value);
                    }
                }
            }
        }
        return headers;
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    static String extractBody(Map<String, Object> payload, String snippet) {
        String plain = findPart(payload, "text/plain", 0);
        if (plain != null && !plain.isBlank()) {
            return normalise(plain);
        }
        String html = findPart(payload, "text/html", 0);
        if (html != null && !html.isBlank()) {
            return normalise(htmlToText(html));
        }
        String direct = bodyData(payload);
        if (direct != null && !direct.isBlank()) {
            return normalise(direct);
        }
        return normalise(snippet);
    }

    private static String findPart(Map<String, Object> payload, String mimeType, int depth) {
        if (depth > 20 || payload == null || payload.isEmpty()) {
            return null;
        }
        String type = string(payload.get("mimeType"));
        String filename = string(payload.get("filename"));
        if (mimeType.equalsIgnoreCase(type) && (filename == null || filename.isBlank())) {
            String data = bodyData(payload);
            if (data != null && !data.isBlank()) {
                return data;
            }
        }
        Object rawParts = payload.get("parts");
        if (rawParts instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> child = (Map<String, Object>) map;
                    String found = findPart(child, mimeType, depth + 1);
                    if (found != null && !found.isBlank()) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String bodyData(Map<String, Object> payload) {
        Object rawBody = payload.get("body");
        if (!(rawBody instanceof Map<?, ?> body)) {
            return null;
        }
        return decode(string(((Map<String, Object>) body).get("data")));
    }

    private static String decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(data.replaceAll("\\s", ""));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String htmlToText(String html) {
        String withBreaks = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n");
        String text = HTML_TAG.matcher(withBreaks).replaceAll(" ");
        return HtmlUtils.htmlUnescape(text);
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        return BLANK_LINES.matcher(normalised).replaceAll("\n\n");
    }

    static Sender parseSender(String value) {
        if (value == null || value.isBlank()) {
            return new Sender(null, null);
        }
        Matcher matcher = SENDER.matcher(value);
        if (matcher.matches()) {
            String name = matcher.group(1).replaceAll("^\"|\"$", "").strip();
            return new Sender(matcher.group(2).strip(), name.isBlank() ? null : name);
        }
        return new Sender(value.strip(), null);
    }

    private static Instant expiry(Object value) {
        if (value instanceof Number number) {
            return Instant.now().plusSeconds(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            return map;
        }
        return Map.of();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Token(String accessToken, Instant expiresAt) {
    }

    public record MessageRef(String id, String threadId) {
    }

    public record Sender(String email, String name) {
    }

    public record GmailMessage(
            String id,
            String threadId,
            String fromEmail,
            String fromName,
            String subject,
            String body,
            String rfcMessageId,
            String references,
            String inReplyTo) {
    }
}
