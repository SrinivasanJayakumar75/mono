package com.monotron.support.knowledge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.PageResponse;
import com.monotron.support.knowledge.dto.ChunkDto;
import com.monotron.support.knowledge.dto.DocumentDetailDto;
import com.monotron.support.knowledge.dto.DocumentDto;
import com.monotron.support.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manages the PDF knowledge base.
 *
 * <p>Uploads are stored on disk and indexed in the background. The document row
 * carries the progress so the UI can follow along.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final String PDF_MIME = "application/pdf";

    private final JdbcTemplate jdbc;
    private final DocumentIngestionService ingestion;
    private final StorageService storage;

    public DocumentService(JdbcTemplate jdbc, DocumentIngestionService ingestion, StorageService storage) {
        this.jdbc = jdbc;
        this.ingestion = ingestion;
        this.storage = storage;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<DocumentDto> list(AuthPrincipal principal, String query, String status, int page, int size) {
        requireWorkspace(principal);
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new java.util.ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(title) like ? or lower(filename) like ?)");
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }

        Long total = jdbc.queryForObject("select count(*) from documents" + where, Long.class, params.toArray());
        params.add(limit);
        params.add(offset);
        List<DocumentDto> documents = jdbc.query("""
                select id, filename, title, status, error, page_count, chunk_count, size_bytes, mime_type,
                       created_at, updated_at
                from documents
                """ + where + " order by created_at desc limit ? offset ?",
                (rs, i) -> new DocumentDto(
                        rs.getString("id"),
                        rs.getString("filename"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("error"),
                        (Integer) rs.getObject("page_count"),
                        (Integer) rs.getObject("chunk_count"),
                        (Long) rs.getObject("size_bytes"),
                        rs.getString("mime_type"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                params.toArray());

        return PageResponse.of(documents, page, limit, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public DocumentDetailDto get(AuthPrincipal principal, String documentId) {
        requireWorkspace(principal);
        DocumentDto document = find(principal, documentId);
        List<ChunkDto> chunks = jdbc.query("""
                select id, chunk_index, title, content, url
                from content_chunks
                where source_type = 'DOCUMENT' and source_id = ?
                order by chunk_index asc
                """, (rs, i) -> new ChunkDto(
                rs.getString("id"),
                rs.getString("chunk_index"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("url")), documentId);

        return new DocumentDetailDto(
                document.id(), document.filename(), document.title(), document.status(), document.error(),
                document.pageCount(), document.chunkCount(), document.sizeBytes(), document.mimeType(),
                document.createdAt(), document.updatedAt(), chunks);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    @Transactional
    public DocumentDto upload(AuthPrincipal principal, MultipartFile file, String title) {
        String workspaceId = requireWorkspace(principal);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a PDF to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The file is larger than the 25 MB limit");
        }
        String filename = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files can be uploaded");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded file could not be read");
        }
        if (!looksLikePdf(content)) {
            throw new BadRequestException("The uploaded file is not a valid PDF");
        }

        String documentId = Ids.uuid();
        String documentTitle = title == null || title.isBlank()
                ? stripExtension(filename)
                : title.trim();
        String path = storagePath(workspaceId, documentId);

        try {
            Files.createDirectories(Path.of(path).getParent());
            Files.write(Path.of(path), content);
        } catch (IOException ex) {
            throw new BadRequestException("The file could not be stored: " + ex.getMessage());
        }

        jdbc.update("""
                insert into documents (id, filename, title, file_path, status, error, uploaded_by,
                                       created_at, updated_at, workspace_id, size_bytes, mime_type,
                                       content_hash, storage_key)
                values (?, ?, ?, ?, 'PENDING', null, ?, now(), now(), ?, ?, ?, ?, ?)
                """, documentId, filename, documentTitle, path, principal.userId(), workspaceId,
                (long) content.length, PDF_MIME, sha256(content), path);

        scheduleIngestion(workspaceId, documentId, path);

        return find(principal, documentId);
    }

    @Transactional
    public DocumentDto reindex(AuthPrincipal principal, String documentId) {
        requireWorkspace(principal);
        DocumentDto document = find(principal, documentId);
        jdbc.update("update documents set status = 'PROCESSING', error = null, updated_at = now() where id = ?",
                documentId);
        ingestion.deleteChunks(documentId);
        scheduleIngestion(requireWorkspace(principal), documentId, pathOf(documentId));
        return find(principal, documentId);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String documentId) {
        requireWorkspace(principal);
        String path = pathOf(documentId);
        ingestion.deleteChunks(documentId);
        jdbc.update("delete from documents where id = ?", documentId);
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException ex) {
            log.warn("Unable to delete the stored file {}", path, ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] loadFile(AuthPrincipal principal, String documentId) {
        requireWorkspace(principal);
        find(principal, documentId);
        String path = pathOf(documentId);
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException ex) {
            throw new NotFoundException("The stored file is no longer available");
        }
    }

    @Transactional(readOnly = true)
    public String loadFileName(AuthPrincipal principal, String documentId) {
        requireWorkspace(principal);
        List<String> names = jdbc.query("select filename from documents where id = ?", (rs, i) -> rs.getString(1),
                documentId);
        return names.isEmpty() ? "document.pdf" : names.get(0);
    }

    // ------------------------------------------------------------------

    private void scheduleIngestion(String workspaceId, String documentId, String path) {
        Runnable trigger = () -> ingestion.ingestAsync(workspaceId, documentId, path);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
    }

    private DocumentDto find(AuthPrincipal principal, String documentId) {
        List<DocumentDto> rows = jdbc.query("""
                select id, filename, title, status, error, page_count, chunk_count, size_bytes, mime_type,
                       created_at, updated_at
                from documents
                where id = ?
                """, (rs, i) -> new DocumentDto(
                rs.getString("id"),
                rs.getString("filename"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("error"),
                (Integer) rs.getObject("page_count"),
                (Integer) rs.getObject("chunk_count"),
                (Long) rs.getObject("size_bytes"),
                rs.getString("mime_type"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), documentId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Document not found");
        }
        return rows.get(0);
    }

    private String pathOf(String documentId) {
        List<String> paths = jdbc.query("select file_path from documents where id = ?", (rs, i) -> rs.getString(1),
                documentId);
        if (paths.isEmpty()) {
            throw new NotFoundException("Document not found");
        }
        return paths.get(0);
    }

    private String storagePath(String workspaceId, String documentId) {
        return storage.pathFor(workspaceId, documentId);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static boolean looksLikePdf(byte[] content) {
        return content.length > 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }
}
