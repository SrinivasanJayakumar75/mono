package com.monotron.support.knowledge;

import java.util.ArrayList;
import java.util.List;

import com.monotron.support.ai.EmbeddingService;
import com.monotron.support.ai.TextChunker;
import com.monotron.support.ai.VectorCodec;
import com.monotron.support.common.Ids;
import com.monotron.support.config.AppProperties;
import com.monotron.support.security.TenantExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a stored PDF into searchable knowledge.
 *
 * <p>The pipeline is: extract text per page, split it into overlapping chunks,
 * embed every chunk and store the vector next to the text. From that point on
 * the document can be retrieved by the AI agent through pgvector.
 *
 * <p>The work happens asynchronously; the document row carries the progress
 * ({@code PENDING} → {@code PROCESSING} → {@code READY} / {@code FAILED}).
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final int EMBEDDING_BATCH_SIZE = 16;

    private final JdbcTemplate jdbc;
    private final PdfTextExtractor extractor;
    private final EmbeddingService embeddings;
    private final TenantExecutor tenantExecutor;
    private final AppProperties properties;

    public DocumentIngestionService(JdbcTemplate jdbc, PdfTextExtractor extractor, EmbeddingService embeddings,
            TenantExecutor tenantExecutor, AppProperties properties) {
        this.jdbc = jdbc;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.tenantExecutor = tenantExecutor;
        this.properties = properties;
    }

    /**
     * Runs the ingestion pipeline for a document in the background.
     *
     * @param workspaceId tenant the document belongs to
     * @param documentId  document to ingest
     * @param filePath    location of the PDF on disk
     */
    @Async
    public void ingestAsync(String workspaceId, String documentId, String filePath) {
        tenantExecutor.run(workspaceId, () -> ingest(workspaceId, documentId, filePath));
    }

    @Transactional
    public void ingest(String workspaceId, String documentId, String filePath) {
        try {
            markStatus(documentId, "PROCESSING", null, null, null);
            byte[] pdf = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath));

            List<PdfTextExtractor.Page> pages = extractor.extract(pdf);
            if (pages.isEmpty()) {
                markStatus(documentId, "FAILED", "No extractable text was found in this PDF. "
                        + "Scanned documents need OCR before they can be indexed.", pages.size(), 0);
                return;
            }

            jdbc.update("delete from content_chunks where source_type = 'DOCUMENT' and source_id = ?", documentId);

            List<Chunk> chunks = new ArrayList<>();
            int index = 0;
            for (PdfTextExtractor.Page page : pages) {
                String text = TextChunker.normalise(page.text());
                int pageChunks = 0;
                for (String part : TextChunker.split(text, properties.getAi().getChunking().getChunkSize(),
                        properties.getAi().getChunking().getChunkOverlap())) {
                    if (part.isBlank()) {
                        continue;
                    }
                    chunks.add(new Chunk(index++, part, page.page()));
                    pageChunks++;
                }
                if (pageChunks == 0) {
                    continue;
                }
            }

            String title = titleOf(documentId);
            embedAndStore(documentId, title, chunks);

            markStatus(documentId, "READY", null, pages.size(), chunks.size());
            log.info("Ingested document {} with {} pages and {} chunks", documentId, pages.size(), chunks.size());
        } catch (Exception ex) {
            log.error("Ingestion of document {} failed", documentId, ex);
            markStatus(documentId, "FAILED", ex.getMessage(), null, null);
        }
    }

    /**
     * Removes every chunk that belongs to a document.
     */
    @Transactional
    public void deleteChunks(String documentId) {
        jdbc.update("delete from content_chunks where source_type = 'DOCUMENT' and source_id = ?", documentId);
    }

    // ------------------------------------------------------------------

    private void embedAndStore(String documentId, String title, List<Chunk> chunks) {
        List<float[]> vectors = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            List<Chunk> batch = chunks.subList(start, Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<String> texts = batch.stream().map(Chunk::text).toList();
            vectors.addAll(embeddings.embedAll(texts));
        }

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            String literal = VectorCodec.toLiteral(vectors.get(i));
            jdbc.update("""
                    insert into content_chunks (id, source_type, source_id, title, url, chunk_index, content,
                                                embedding, created_at, token_count, metadata)
                    values (?, 'DOCUMENT', ?, ?, null, ?, ?, ?::vector, now(), ?, ?::jsonb)
                    """,
                    Ids.uuid(),
                    documentId,
                    title,
                    chunk.index(),
                    chunk.text(),
                    literal,
                    estimateTokens(chunk.text()),
                    "{\"page\":" + chunk.page() + "}");
        }
    }

    private void markStatus(String documentId, String status, String error, Integer pageCount, Integer chunkCount) {
        jdbc.update("""
                update documents
                set status = ?, error = ?,
                    page_count = coalesce(?, page_count),
                    chunk_count = coalesce(?, chunk_count),
                    updated_at = now()
                where id = ?
                """, status, error, pageCount, chunkCount, documentId);
    }

    private String titleOf(String documentId) {
        List<String> titles = jdbc.query("select title from documents where id = ?", (rs, i) -> rs.getString(1),
                documentId);
        return titles.isEmpty() ? "Document" : titles.get(0);
    }

    static int estimateTokens(String text) {
        return text == null ? 0 : (int) Math.ceil(text.length() / 4.0);
    }

    private record Chunk(int index, String text, int page) {
    }
}
