package com.monotron.support.channels.dto;

/**
 * A provider authorization URL the browser should navigate to.
 */
public record OAuthStartDto(String channel, String authorizationUrl) {
}
