package com.monotron.support.channels;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Meta webhook endpoint for both WhatsApp Cloud API and Instagram
 * Messaging. Meta verifies the endpoint with a GET challenge, then sends signed
 * POST events.
 *
 * <p>The endpoint intentionally has no bearer token. The workspace is resolved
 * from the provider account id inside the payload, and the signature is checked
 * against the raw request body before any message is processed.
 */
@RestController
@RequestMapping("/api/channel-webhooks/meta")
public class MetaWebhookController {

    private final MetaWebhookService webhookService;

    public MetaWebhookController(MetaWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && webhookService.validVerifyToken(token)) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {
        byte[] payload = body == null ? new byte[0] : body;
        if (!webhookService.validSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }
        webhookService.processAsync(payload);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
