package com.monotron.support.crm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.monotron.support.common.PageResponse;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the built-in CRM.
 *
 * <p>Leads, deals, companies, contacts, tasks, calls, meetings, products,
 * orders and documents share the same CRUD shape, so one resource route
 * drives them all. Order line items have their own nested routes.
 */
@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crm;

    public CrmController(CrmService crm) {
        this.crm = crm;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal AuthPrincipal principal) {
        return crm.overview(principal);
    }

    @GetMapping("/leads/{leadId}/profile")
    public Map<String, Object> leadProfile(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String leadId) {
        return crm.leadProfile(principal, leadId);
    }

    @PostMapping("/leads/from-contact/{contactId}")
    public ResponseEntity<Map<String, Object>> createLeadFromContact(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String contactId) {
        Map<String, Object> result = crm.createLeadFromContact(principal, contactId);
        HttpStatus status = Boolean.TRUE.equals(result.get("created"))
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/orders/{orderId}/items")
    public List<Map<String, Object>> orderItems(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String orderId) {
        return crm.orderItems(principal, orderId);
    }

    @PostMapping("/orders/{orderId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addOrderItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {
        return crm.addOrderItem(principal, orderId, body);
    }

    @DeleteMapping("/orders/{orderId}/items/{itemId}")
    public ResponseEntity<Void> deleteOrderItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String orderId,
            @PathVariable String itemId) {
        crm.deleteOrderItem(principal, orderId, itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{resource}")
    public PageResponse<Map<String, Object>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("filter.") && key.length() > "filter.".length()
                    && value != null && !value.isBlank()) {
                filters.put(key.substring("filter.".length()), value);
            }
        });
        return crm.list(principal, resource, query, page, size, filters);
    }

    @GetMapping("/{resource}/{id}")
    public Map<String, Object> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @PathVariable String id) {
        return crm.get(principal, resource, id);
    }

    @PostMapping("/{resource}/import")
    public Map<String, Object> importRows(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @RequestBody ImportRequest request) {
        return crm.importRows(principal, resource, request.rows());
    }

    @PostMapping("/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        return crm.create(principal, resource, body);
    }

    @PatchMapping("/{resource}/{id}")
    public Map<String, Object> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return crm.update(principal, resource, id, body);
    }

    @DeleteMapping("/{resource}/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String resource,
            @PathVariable String id) {
        crm.delete(principal, resource, id);
        return ResponseEntity.noContent().build();
    }

    /** A batch of rows already mapped to CRM field keys by the import UI. */
    public record ImportRequest(List<Map<String, Object>> rows) {
    }
}
