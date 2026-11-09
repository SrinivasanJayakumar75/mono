package com.monotron.support.channels;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.monotron.support.channels.GmailApiClient.Sender;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailApiClientTest {

    private static final Base64.Encoder BASE64URL =
            Base64.getUrlEncoder().withoutPadding();

    @Test
    @DisplayName("plain text is preferred over the HTML alternative")
    void prefersPlainText() {
        Map<String, Object> plain = part("text/plain", "Hello from plain text");
        Map<String, Object> html = part("text/html", "<p>Hello from HTML</p>");
        Map<String, Object> payload = Map.of(
                "mimeType", "multipart/alternative",
                "parts", List.of(html, plain));

        assertThat(GmailApiClient.extractBody(payload, "snippet"))
                .isEqualTo("Hello from plain text");
    }

    @Test
    @DisplayName("an HTML-only email is converted to readable text")
    void convertsHtmlToText() {
        Map<String, Object> html = part("text/html", "<p>Hello</p><p>team</p>");
        Map<String, Object> payload = Map.of(
                "mimeType", "multipart/alternative",
                "parts", List.of(html));

        String body = GmailApiClient.extractBody(payload, "");

        assertThat(body).contains("Hello").contains("team").doesNotContain("<p>");
    }

    @Test
    @DisplayName("the snippet is used when no body part is present")
    void fallsBackToSnippet() {
        assertThat(GmailApiClient.extractBody(Map.of("mimeType", "text/plain"), "Just a snippet"))
                .isEqualTo("Just a snippet");
    }

    @Test
    @DisplayName("a display name and address are separated")
    void parsesSender() {
        Sender sender = GmailApiClient.parseSender("\"Asha Rao\" <asha@example.com>");

        assertThat(sender.email()).isEqualTo("asha@example.com");
        assertThat(sender.name()).isEqualTo("Asha Rao");
    }

    @Test
    @DisplayName("a bare address is accepted")
    void parsesBareSender() {
        Sender sender = GmailApiClient.parseSender("asha@example.com");

        assertThat(sender.email()).isEqualTo("asha@example.com");
        assertThat(sender.name()).isNull();
    }

    @Test
    @DisplayName("a reply is a valid RFC message with threading headers")
    void buildsReplyMessage() throws Exception {
        String raw = GmailApiClient.rawMessage(
                "support@example.com",
                "customer@example.com",
                "Re: Need help",
                "<incoming@example.com>",
                "<incoming@example.com>",
                "Here is the answer.");

        byte[] decoded = Base64.getUrlDecoder().decode(raw);
        MimeMessage message = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(decoded));
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Re: Need help");
        assertThat(message.getFrom()[0].toString()).isEqualTo("support@example.com");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("customer@example.com");
        assertThat(message.getHeader("In-Reply-To", null)).isEqualTo("<incoming@example.com>");
        assertThat(message.getContent()).isEqualTo("Here is the answer.");
    }

    private static Map<String, Object> part(String mimeType, String text) {
        String encoded = BASE64URL.encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return Map.of(
                "mimeType", mimeType,
                "body", Map.of("data", encoded));
    }
}
