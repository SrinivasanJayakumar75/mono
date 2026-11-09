package com.monotron.support.channels;

import com.monotron.support.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelOAuthStateServiceTest {

    private static ChannelOAuthStateService service() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("unit-test-secret-that-is-long-enough-for-hs256");
        properties.getChannels().setStateSecret("unit-test-state-secret");
        return new ChannelOAuthStateService(properties);
    }

    @Test
    @DisplayName("a signed state carries the workspace and channel")
    void roundTrip() {
        ChannelOAuthStateService service = service();

        ChannelOAuthStateService.State state = service.parse(
                        service.issue("workspace-1", "WHATSAPP"))
                .orElseThrow();

        assertThat(state.workspaceId()).isEqualTo("workspace-1");
        assertThat(state.channel()).isEqualTo("WHATSAPP");
    }

    @Test
    @DisplayName("a tampered state is rejected")
    void rejectsTampering() {
        ChannelOAuthStateService service = service();
        String issued = service.issue("workspace-1", "GMAIL");

        assertThat(service.parse(issued + "x")).isEmpty();
        assertThat(service.parse("not-a-state")).isEmpty();
        assertThat(service.parse(null)).isEmpty();
    }
}
