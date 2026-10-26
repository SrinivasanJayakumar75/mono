package com.monotron.support.tenancy.dto;

/**
 * A platform user as seen by the caller.
 */
public record UserDto(
        String id,
        String email,
        String name,
        String role,
        String status) {
}
