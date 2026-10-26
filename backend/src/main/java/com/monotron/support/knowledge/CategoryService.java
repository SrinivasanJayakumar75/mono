package com.monotron.support.knowledge;

import java.util.List;

import com.monotron.support.common.ConflictException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.knowledge.dto.CategoryDto;
import com.monotron.support.knowledge.dto.CategoryRequest;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories used to group knowledge base articles.
 */
@Service
public class CategoryService {

    private final JdbcTemplate jdbc;

    public CategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list(AuthPrincipal principal) {
        requireWorkspace(principal);
        return jdbc.query("""
                select id, name, slug, sort_order from categories where workspace_id = ? order by sort_order asc, name asc
                """, (rs, i) -> new CategoryDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getInt("sort_order")), principal.workspaceId());
    }

    @Transactional
    public CategoryDto create(AuthPrincipal principal, CategoryRequest request) {
        String workspaceId = requireWorkspace(principal);
        String name = request.name().trim();
        String slug = Ids.slugify(name);
        if (slug.isEmpty()) {
            throw new ForbiddenException("That category name cannot be used");
        }
        Integer existing = jdbc.queryForObject(
                "select count(*) from categories where workspace_id = ? and slug = ?", Integer.class, workspaceId, slug);
        if (existing != null && existing > 0) {
            throw new ConflictException("A category with that name already exists");
        }
        String categoryId = Ids.uuid();
        jdbc.update("""
                insert into categories (id, name, slug, sort_order, created_at, workspace_id)
                values (?, ?, ?, ?, now(), ?)
                """, categoryId, name, slug, request.sortOrder() == null ? 0 : request.sortOrder(), workspaceId);
        return find(categoryId);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String categoryId) {
        requireWorkspace(principal);
        jdbc.update("delete from categories where id = ?", categoryId);
    }

    private CategoryDto find(String categoryId) {
        List<CategoryDto> rows = jdbc.query("""
                select id, name, slug, sort_order from categories where id = ?
                """, (rs, i) -> new CategoryDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getInt("sort_order")), categoryId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Category not found");
        }
        return rows.get(0);
    }

    private String requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
        return principal.workspaceId();
    }
}
