package com.monotron.support.knowledge;

import java.util.List;

import com.monotron.support.common.PageResponse;
import com.monotron.support.knowledge.dto.ChunkDto;
import com.monotron.support.knowledge.dto.DocumentDetailDto;
import com.monotron.support.knowledge.dto.DocumentDto;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF knowledge base.
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/documents")
    public PageResponse<DocumentDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(name = "q", required = false) String q,
                                         @RequestParam(name = "status", required = false) String status,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "size", defaultValue = "20") int size) {
        return documentService.list(principal, q, status, page, size);
    }

    @GetMapping("/api/documents/{id}")
    public DocumentDetailDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return documentService.get(principal, id);
    }

    @PostMapping(path = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto upload(@AuthenticationPrincipal AuthPrincipal principal,
                              @RequestPart("file") MultipartFile file,
                              @RequestPart(value = "title", required = false) String title) {
        return documentService.upload(principal, file, title);
    }

    @PostMapping("/api/documents/{id}/reindex")
    public DocumentDto reindex(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return documentService.reindex(principal, id);
    }

    @GetMapping("/api/documents/{id}/file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        byte[] content = documentService.loadFile(principal, id);
        String filename = documentService.loadFileName(principal, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @DeleteMapping("/api/documents/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        documentService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    /** Convenience endpoint that exposes the chunks of a document. */
    @GetMapping("/api/documents/{id}/chunks")
    public ChunksResponse chunks(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        DocumentDetailDto detail = documentService.get(principal, id);
        return new ChunksResponse(detail.chunks());
    }

    public record ChunksResponse(List<ChunkDto> items) {
    }
}
