package com.monotron.support.channels;

import com.monotron.support.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelCredentialCryptoTest {

    private static ChannelCredentialCrypto crypto() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("unit-test-secret-that-is-long-enough-for-hs256");
        properties.getChannels().setEncryptionKey("unit-test-channel-key-0123456789");
        return new ChannelCredentialCrypto(properties);
    }

    @Test
    @DisplayName("tokens round-trip through AES-GCM")
    void roundTrip() {
        ChannelCredentialCrypto crypto = crypto();

        String encrypted = crypto.encrypt("access-token-value");

        assertThat(encrypted).startsWith("v1:").doesNotContain("access-token-value");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("access-token-value");
    }

    @Test
    @DisplayName("a fresh IV is used for every encryption")
    void usesFreshIv() {
        ChannelCredentialCrypto crypto = crypto();

        assertThat(crypto.encrypt("same-value")).isNotEqualTo(crypto.encrypt("same-value"));
    }

    @Test
    @DisplayName("missing values stay null")
    void missingValue() {
        ChannelCredentialCrypto crypto = crypto();

        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.decrypt("")).isNull();
    }
}
