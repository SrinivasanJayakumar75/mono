package com.monotron.support.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;

/**
 * The authenticated principal: a platform user together with the workspace
 * (tenant) that the current request is scoped to.
 *
 * @param userId      platform user id
 * @param email       login email
 * @param name        display name
 * @param workspaceId active tenant, may be {@code null} when the user has no membership
 * @param role        role inside the active workspace
 */
public record AuthPrincipal(
        String userId,
        String email,
        String name,
        String workspaceId,
        String role) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_AGENT = "AGENT";
    public static final String ROLE_VIEWER = "VIEWER";

    public boolean hasWorkspace() {
        return workspaceId != null && !workspaceId.isBlank();
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "VIEWER" : role.toUpperCase())));
    }

    @SuppressWarnings("unchecked")
    public static AuthPrincipal from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal p)) {
            return null;
        }
        return p;
    }
}
