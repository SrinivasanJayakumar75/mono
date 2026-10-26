package com.monotron.support.security;

import java.io.IOException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code Authorization: Bearer <token>} header, validates it and
 * populates the {@link SecurityContext} with an {@link AuthPrincipal}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            Claims claims = jwtService.parse(header.substring(PREFIX.length()).trim());
            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String workspaceId = claims.get(JwtService.CLAIM_WORKSPACE, String.class);
                AuthPrincipal principal = new AuthPrincipal(
                        claims.getSubject(),
                        claims.get(JwtService.CLAIM_EMAIL, String.class),
                        claims.get(JwtService.CLAIM_NAME, String.class),
                        workspaceId,
                        claims.get(JwtService.CLAIM_ROLE, String.class));
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.authorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
