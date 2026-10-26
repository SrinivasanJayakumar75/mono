package com.monotron.support.billing;

import java.util.Map;

import com.monotron.support.billing.dto.BillingSummaryDto;
import com.monotron.support.billing.dto.CheckoutRequest;
import com.monotron.support.billing.dto.CheckoutResponse;
import com.monotron.support.billing.dto.VerifyPaymentRequest;
import com.monotron.support.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay subscription endpoints. The webhook is the only public route; all
 * other routes require a workspace admin.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public BillingSummaryDto summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.summary(principal);
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CheckoutRequest request) {
        return billingService.checkout(principal, request.interval());
    }

    @PostMapping("/verify")
    public BillingSummaryDto verify(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return billingService.verify(principal, request);
    }

    @PostMapping("/cancel")
    public BillingSummaryDto cancel(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.cancel(principal);
    }

    @PostMapping("/webhook")
    public Map<String, String> webhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestBody String body) {
        billingService.handleWebhook(body, signature, eventId);
        return Map.of("status", "ok");
    }
}
