package com.monotron.support.widget;

import java.util.List;

import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.widget.dto.WidgetContentDto;
import com.monotron.support.widget.dto.WidgetContentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace-side management of blogs, FAQs and news announcements shown in the
 * messenger widget.
 */
@RestController
@RequestMapping("/api/widget-content")
public class WidgetContentController {

    private final WidgetContentService contentService;

    public WidgetContentController(WidgetContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    public List<WidgetContentDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return contentService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WidgetContentDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody WidgetContentRequest request) {
        return contentService.create(principal, request);
    }

    @PutMapping("/{id}")
    public WidgetContentDto update(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable String id,
                                   @Valid @RequestBody WidgetContentRequest request) {
        return contentService.update(principal, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable String id) {
        contentService.delete(principal, id);
    }
}
