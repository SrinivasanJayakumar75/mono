package com.monotron.support.knowledge;

import com.monotron.support.ai.RagService;
import com.monotron.support.ai.dto.SearchHitDto;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.knowledge.dto.ArticleDto;
import com.monotron.support.knowledge.dto.ArticleRequest;
import com.monotron.support.knowledge.dto.CategoryDto;
import com.monotron.support.knowledge.dto.CategoryRequest;
import com.monotron.support.knowledge.dto.SearchRequest;
import com.monotron.support.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Knowledge base browsing, authoring and semantic search.
 */
@RestController
public class KnowledgeController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final RagService ragService;

    public KnowledgeController(ArticleService articleService, CategoryService categoryService, RagService ragService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.ragService = ragService;
    }

    // ------------------------------------------------------------------
    // Articles
    // ------------------------------------------------------------------

    @GetMapping("/api/articles")
    public com.monotron.support.common.PageResponse<ArticleDto> listArticles(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "published", required = false) Boolean published,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return articleService.list(principal, q, categoryId, published, page, size);
    }

    @GetMapping("/api/articles/{id}")
    public ArticleDto getArticle(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return articleService.get(principal, id);
    }

    @PostMapping("/api/articles")
    public ArticleDto createArticle(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody ArticleRequest request) {
        return articleService.create(principal, request);
    }

    @PutMapping("/api/articles/{id}")
    public ArticleDto updateArticle(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable String id,
                                    @Valid @RequestBody ArticleRequest request) {
        return articleService.update(principal, id, request);
    }

    @DeleteMapping("/api/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        articleService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    @GetMapping("/api/categories")
    public CategoriesResponse categories(@AuthenticationPrincipal AuthPrincipal principal) {
        return new CategoriesResponse(categoryService.list(principal));
    }

    @PostMapping("/api/categories")
    public CategoryDto createCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(principal, request);
    }

    @DeleteMapping("/api/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        categoryService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Semantic search
    // ------------------------------------------------------------------

    @PostMapping("/api/search")
    public SearchResponse search(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody SearchRequest request) {
        requireWorkspace(principal);
        List<SearchHitDto> hits = ragService.search(request.query(),
                request.limit() == null ? 8 : request.limit());
        return new SearchResponse(hits);
    }

    private void requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
    }

    public record CategoriesResponse(List<CategoryDto> items) {
    }

    public record SearchResponse(List<SearchHitDto> items) {
    }
}
