package com.monotron.support.security;

import java.time.Duration;

import com.monotron.support.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static JwtService service() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("unit-test-secret-that-is-long-enough-for-hs256");
        properties.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));
        properties.getJwt().setIssuer("monotron-test");
        return new JwtService(properties);
    }

    @Test
    @DisplayName("an issued token carries the tenant, role and identity")
    void roundTrip() {
        JwtService service = service();
        String token = service.issueAccessToken("user-1", "a@b.com", "Ada", "ws-1", "ADMIN");

        Claims claims = service.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.getIssuer()).isEqualTo("monotron-test");
        assertThat(claims.get(JwtService.CLAIM_WORKSPACE, String.class)).isEqualTo("ws-1");
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("a token without a workspace omits the tenant claim")
    void tokenWithoutWorkspace() {
        Claims claims = service().parse(service().issueAccessToken("u", "e", "n", null, "AGENT"));
        assertThat(claims).isNotNull();
        assertThat(claims.get(JwtService.CLAIM_WORKSPACE)).isNull();
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void rejectsTamperedToken() {
        JwtService service = service();
        String token = service.issueAccessToken("user-1", "a@b.com", "Ada", "ws-1", "ADMIN");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.parse(tampered)).isNull();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignToken() {
        AppProperties other = new AppProperties();
        other.getJwt().setSecret("a-completely-different-secret-that-is-long-enough");
        JwtService foreign = new JwtService(other);

        String token = foreign.issueAccessToken("user-1", "a@b.com", "Ada", "ws-1", "ADMIN");

        assertThat(service().parse(token)).isNull();
    }

    @Test
    @DisplayName("blank tokens are rejected without throwing")
    void rejectsBlankToken() {
        assertThat(service().parse(null)).isNull();
        assertThat(service().parse("  ")).isNull();
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes is refused")
    void refusesShortSecret() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("too-short");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
