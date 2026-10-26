package com.monotron.support.knowledge;

import java.util.List;
import java.util.Locale;

import com.monotron.support.ai.EmbeddingService;
import com.monotron.support.ai.TextChunker;
import com.monotron.support.ai.VectorCodec;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.PageResponse;
import com.monotron.support.config.AppProperties;
import com.monotron.support.knowledge.dto.ArticleDto;
import com.monotron.support.knowledge.dto.ArticleRequest;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge base articles.
 *
 * <p>Articles are written by the support team but are indexed exactly like the
 * uploaded PDFs, so the AI agent can ground its answers in them too.
 */
@Service
public class ArticleService {

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    private final AppProperties properties;

    public ArticleService(JdbcTemplate jdbc, EmbeddingService embeddings, AppProperties properties) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleDto> list(AuthPrincipal principal, String query, String categoryId, Boolean published,
            int page, int size) {
        requireWorkspace(principal);
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new java.util.ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" and (lower(a.title) like ? or lower(a.body) like ?)");
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }
        if (categoryId != null && !categoryId.isBlank()) {
            where.append(" and a.category_id = ?");
            params.add(categoryId);
        }
        if (published != null) {
            where.append(" and a.published = ?");
            params.add(published);
        }

        Long total = jdbc.queryForObject("select count(*) from articles a" + where, Long.class, params.toArray());
        params.add(limit);
        params.add(offset);
        List<ArticleDto> articles = jdbc.query("""
                select a.id, a.title, a.slug, a.body, a.published, a.category_id, c.name as category_name,
                       u.name as author_name, a.created_at, a.updated_at
                from articles a
                left join categories c on c.id = a.category_id
                left join users u on u.id = a.author_id
                """ + where + " order by a.updated_at desc limit ? offset ?",
                (rs, i) -> new ArticleDto(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("slug"),
                        rs.getString("body"),
                        rs.getBoolean("published"),
                        rs.getString("category_id"),
                        rs.getString("category_name"),
                        rs.getString("author_name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                params.toArray());

        return PageResponse.of(articles, page, limit, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ArticleDto get(AuthPrincipal principal, String articleId) {
        requireWorkspace(principal);
        return find(articleId);
    }

    @Transactional
    public ArticleDto create(AuthPrincipal principal, ArticleRequest request) {
        requireWorkspace(principal);
        String articleId = Ids.uuid();
        String slug = uniqueSlug(principal.workspaceId(), request.title());
        jdbc.update("""
                insert into articles (id, category_id, title, slug, body, published, author_id,
                                      created_at, updated_at, workspace_id)
                values (?, ?, ?, ?, ?, ?, ?, now(), now(), ?)
                """, articleId, blankToNull(request.categoryId()), request.title().trim(), slug, request.body(),
                request.published() == null || request.published(), principal.userId(), principal.workspaceId());

        index(articleId, request.title().trim(), request.body());
        return find(articleId);
    }

    @Transactional
    public ArticleDto update(AuthPrincipal principal, String articleId, ArticleRequest request) {
        requireWorkspace(principal);
        find(articleId);
        jdbc.update("""
                update articles
                set title = ?, body = ?, category_id = ?, published = ?, updated_at = now()
                where id = ?
                """, request.title().trim(), request.body(), blankToNull(request.categoryId()),
                request.published() == null || request.published(), articleId);

        index(articleId, request.title().trim(), request.body());
        return find(articleId);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String articleId) {
        requireWorkspace(principal);
        find(articleId);
        jdbc.update("delete from content_chunks where source_type = 'ARTICLE' and source_id = ?", articleId);
        jdbc.update("delete from articles where id = ?", articleId);
    }

    // ------------------------------------------------------------------

    /**
     * Chunks and embeds an article so it becomes part of the AI's knowledge.
     */
    private void index(String articleId, String title, String body) {
        jdbc.update("delete from content_chunks where source_type = 'ARTICLE' and source_id = ?", articleId);
        List<String> chunks = TextChunker.split(TextChunker.normalise(body),
                properties.getAi().getChunking().getChunkSize(),
                properties.getAi().getChunking().getChunkOverlap());
        if (chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = embeddings.embedAll(chunks);
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("""
                    insert into content_chunks (id, source_type, source_id, title, url, chunk_index, content,
                                                embedding, created_at, token_count, metadata)
                    values (?, 'ARTICLE', ?, ?, null, ?, ?, ?::vector, now(), ?, '{}'::jsonb)
                    """, Ids.uuid(), articleId, title, i, chunks.get(i), VectorCodec.toLiteral(vectors.get(i)),
                    DocumentIngestionService.estimateTokens(chunks.get(i)));
        }
    }

    private ArticleDto find(String articleId) {
        List<ArticleDto> rows = jdbc.query("""
                select a.id, a.title, a.slug, a.body, a.published, a.category_id, c.name as category_name,
                       u.name as author_name, a.created_at, a.updated_at
                from articles a
                left join categories c on c.id = a.category_id
                left join users u on u.id = a.author_id
                where a.id = ?
                """, (rs, i) -> new ArticleDto(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("body"),
                rs.getBoolean("published"),
                rs.getString("category_id"),
                rs.getString("category_name"),
                rs.getString("author_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), articleId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Article not found");
        }
        return rows.get(0);
    }

    private String uniqueSlug(String workspaceId, String title) {
        String base = Ids.slugify(title);
        if (base.isEmpty()) {
            base = "article";
        }
        String candidate = base;
        int suffix = 2;
        while (slugTaken(workspaceId, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugTaken(String workspaceId, String slug) {
        Integer count = jdbc.queryForObject("select count(*) from articles where workspace_id = ? and slug = ?",
                Integer.class, workspaceId, slug);
        return count != null && count > 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }
}
