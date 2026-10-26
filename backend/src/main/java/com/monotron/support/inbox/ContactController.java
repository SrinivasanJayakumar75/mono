package com.monotron.support.inbox;

import java.util.List;

import com.monotron.support.common.PageResponse;
import com.monotron.support.inbox.dto.ContactDetailDto;
import com.monotron.support.inbox.dto.ContactSummaryDto;
import com.monotron.support.inbox.dto.ConversationDto;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The visitor directory.
 */
@RestController
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/api/contacts")
    public PageResponse<ContactSummaryDto> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return contactService.list(principal, q, page, size);
    }

    @GetMapping("/api/contacts/{id}")
    public ContactDetail get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        ContactDetailDto contact = contactService.get(principal, id);
        List<ConversationDto> conversations = contactService.conversationsOf(principal, id);
        return new ContactDetail(contact, conversations);
    }

    /**
     * A contact together with the conversations they started.
     */
    public record ContactDetail(ContactDetailDto contact, List<ConversationDto> conversations) {
    }
}
