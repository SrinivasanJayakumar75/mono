package com.monotron.support.billing;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotron.support.common.ApiError;
import com.monotron.support.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the subscription paywall for every authenticated workspace API.
 *
 * <p>The billing and auth endpoints are excluded so an expired workspace can
 * still load its session and pay. The widget is excluded because it is public.
 * A 402 tells the browser to move to the billing screen.
 */
@Component
public class BillingAccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BillingAccessInterceptor.class);

    private final BillingService billingService;
    private final ObjectMapper mapper;

    public BillingAccessInterceptor(BillingService billingService, ObjectMapper mapper) {
        this.billingService = billingService;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod)) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/auth") || path.startsWith("/api/billing")
                || path.equals("/api/widget") || path.startsWith("/api/widget/")) {
            return true;
        }

        AuthPrincipal principal = AuthPrincipal.from(SecurityContextHolder.getContext().getAuthentication());
        if (principal == null || !principal.hasWorkspace()) {
            return true;
        }

        boolean access;
        try {
            access = billingService.hasAccess(principal.workspaceId());
        } catch (RuntimeException ex) {
            log.error("Could not verify billing access for workspace {}", principal.workspaceId(), ex);
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ApiError.of(503, "Service Unavailable",
                            "Unable to verify your subscription right now. Please try again.",
                            path));
            return false;
        }

        if (access) {
            return true;
        }

        write(response, 402, ApiError.of(402, "Payment Required",
                "Your trial has ended. Choose a plan to continue using Monotron.", path));
        return false;
    }

    private void write(HttpServletResponse response, int status, ApiError error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), error);
    }
}
