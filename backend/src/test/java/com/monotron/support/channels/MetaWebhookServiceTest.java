package com.monotron.support.channels;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.ai.RagService;
import com.monotron.support.config.AppProperties;
import com.monotron.support.realtime.RealtimePublisher;
import com.monotron.support.security.TenantBinding;
import com.monotron.support.tenancy.AppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MetaWebhookServiceTest {

    @Test
    @DisplayName("the X-Hub-Signature-256 header is checked against the raw body")
    void verifiesMetaSignature() throws Exception {
        MetaWebhookService service = service("app-secret", "instagram-secret", "verify-me");
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.validSignature(body, signature("app-secret", body))).isTrue();
        assertThat(service.validSignature(body, signature("instagram-secret", body))).isTrue();
        assertThat(service.validSignature(body, "sha256=00")).isFalse();
        assertThat(service.validSignature(body, null)).isFalse();
    }

    @Test
    @DisplayName("the webhook verify token is compared exactly")
    void verifiesWebhookToken() {
        MetaWebhookService service = service("app-secret", "instagram-secret", "verify-me");

        assertThat(service.validVerifyToken("verify-me")).isTrue();
        assertThat(service.validVerifyToken("wrong")).isFalse();
        assertThat(service.validVerifyToken(null)).isFalse();
    }

    private static MetaWebhookService service(String appSecret, String instagramAppSecret,
                                              String verifyToken) {
        AppProperties properties = new AppProperties();
        properties.getChannels().getMeta().setAppId("app-id");
        properties.getChannels().getMeta().setAppSecret(appSecret);
        properties.getChannels().getMeta().setInstagramAppId("instagram-app-id");
        properties.getChannels().getMeta().setInstagramAppSecret(instagramAppSecret);
        properties.getChannels().getMeta().setWebhookVerifyToken(verifyToken);
        return new MetaWebhookService(
                mock(JdbcTemplate.class),
                mock(AppService.class),
                mock(RagService.class),
                mock(MetaApiClient.class),
                mock(ChannelCredentialCrypto.class),
                mock(RealtimePublisher.class),
                new ObjectMapper(),
                properties,
                mock(TenantBinding.class),
                mock(PlatformTransactionManager.class));
    }

    private static String signature(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
