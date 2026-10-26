package com.monotron.support.crm;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.monotron.support.common.BadRequestException;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.common.Ids;
import com.monotron.support.common.NotFoundException;
import com.monotron.support.common.PageResponse;
import com.monotron.support.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRM persistence. Every table it touches is protected by Row Level Security,
 * so queries are implicitly scoped to the caller's workspace.
 */
@Service
public class CrmService {

    private static final int MAX_IMPORT_ROWS = 2_000;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> RELATED_TABLES = Map.of(
            "LEAD", "leads",
            "DEAL", "deals",
            "COMPANY", "companies",
            "CONTACT", "crm_contacts",
            "ORDER", "orders");
    private static final Map<String, String> RELATED_NAME_EXPRESSIONS = Map.of(
            "LEAD", "name",
            "DEAL", "title",
            "COMPANY", "name",
            "CONTACT", "trim(first_name || ' ' || coalesce(last_name, ''))",
            "ORDER", "order_number");

    private final JdbcTemplate jdbc;

    public CrmService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ----------------------------- generic CRUD ----------------------------- */

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(
            AuthPrincipal principal,
            String resourceKey,
            String query,
            int page,
            int size,
            Map<String, String> filters) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);

        int limit = Math.max(1, Math.min(size, 200));
        int offset = Math.max(0, page) * limit;

        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            where.append(" and (");
            for (int i = 0; i < resource.searchColumns().size(); i++) {
                if (i > 0) {
                    where.append(" or ");
                }
                where.append(resource.searchColumns().get(i));
                params.add(like);
            }
            where.append(")");
        }

        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                String raw = entry.getValue();
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                CrmField field = resource.field(key);
                if (field == null) {
                    throw new BadRequestException("Unknown filter: " + key);
                }
                Object value = coerce(field, raw);
                if (value == null) {
                    continue;
                }
                if (field.type() == CrmField.Type.TEXT) {
                    // Cast keeps relation-id columns (uuid) comparable without
                    // adding a second, type-specific query path.
                    where.append(" and lower(cast(t.")
                            .append(field.column())
                            .append(" as text)) = lower(?)");
                } else {
                    where.append(" and t.").append(field.column()).append(" = ?");
                }
                params.add(value);
            }
        }

        Long total = jdbc.queryForObject(
                "select count(*) " + resource.fromSql() + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(limit);
        pageParams.add(offset);

        List<Map<String, Object>> items = jdbc.query(
                "select " + resource.selectColumns() + " " + resource.fromSql() + where
                        + " order by " + resource.orderBy() + " limit ? offset ?",
                rowMapper(), pageParams.toArray());

        return PageResponse.of(items, page, limit, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(AuthPrincipal principal, String resourceKey, String id) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);
        List<Map<String, Object>> rows = jdbc.query(
                "select " + resource.selectColumns() + " " + resource.fromSql() + " where t.id = ?",
                rowMapper(), id);
        if (rows.isEmpty()) {
            throw new NotFoundException(resource.label() + " not found");
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> create(AuthPrincipal principal, String resourceKey, Map<String, Object> body) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);
        PreparedInsert prepared = prepareInsert(
                resource, body, false, false, new HashMap<>(), new HashMap<>());
        String id = insert(resource, prepared);
        return get(principal, resourceKey, id);
    }

    /**
     * Promotes an inbox visitor into a CRM lead.
     *
     * <p>The link is stored on the lead, so clicking the button twice (or two
     * agents clicking at once) returns the same lead instead of creating a
     * duplicate. Contact details are copied once; later edits are independent.
     */
    @Transactional
    public Map<String, Object> createLeadFromContact(AuthPrincipal principal, String contactId) {
        requireWorkspace(principal);
        String id = text(contactId);
        if (id == null) {
            throw new BadRequestException("A contact id is required");
        }

        List<Map<String, Object>> contacts = jdbc.query(
                "select id, name, email, location from contacts where id = ?",
                rowMapper(), id);
        if (contacts.isEmpty()) {
            throw new NotFoundException("Inbox contact not found");
        }

        Map<String, Object> existing = findLeadByContact(id);
        if (existing != null) {
            return leadFromContactResult(false, existing);
        }

        Map<String, Object> contact = contacts.get(0);
        String email = text(contact.get("email"));
        String name = text(contact.get("name"));
        if (name == null) {
            int at = email == null ? -1 : email.indexOf('@');
            name = at > 0 ? email.substring(0, at) : (email == null ? "Inbox visitor" : email);
        }

        String firstName = name;
        String lastName = null;
        int space = name.indexOf(' ');
        if (space > 0) {
            firstName = name.substring(0, space).trim();
            lastName = text(name.substring(space + 1));
        }

        String leadId = Ids.uuid();
        int inserted = jdbc.update("""
                insert into leads (id, name, first_name, last_name, email, location, source,
                                   status, priority, buying_intent, notes, source_contact_id)
                values (?, ?, ?, ?, ?, ?, 'WEBSITE', 'NEW', 'MEDIUM', 'MEDIUM', ?, ?)
                on conflict (workspace_id, source_contact_id)
                    where source_contact_id is not null do nothing
                """, leadId, truncate(name, 200), truncate(firstName, 100),
                truncate(lastName, 100), truncate(email, 255),
                truncate(text(contact.get("location")), 200),
                "Created from the Monotron inbox.", id);

        if (inserted == 0) {
            existing = findLeadByContact(id);
            if (existing != null) {
                return leadFromContactResult(false, existing);
            }
            throw new NotFoundException("Inbox contact not found");
        }

        return leadFromContactResult(true, get(principal, "leads", leadId));
    }

    private Map<String, Object> findLeadByContact(String contactId) {
        CrmResource leads = resource("leads");
        List<Map<String, Object>> rows = jdbc.query(
                "select " + leads.selectColumns() + " " + leads.fromSql()
                        + " where t.source_contact_id = ? order by t.created_at desc limit 1",
                rowMapper(), contactId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Map<String, Object> leadFromContactResult(
            boolean created, Map<String, Object> lead) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("lead", lead);
        return out;
    }

    /**
     * Bulk-create records from a CSV import. Every row is validated before
     * anything is written, so a bad row never leaves a half-imported file
     * behind. Validation failures are returned as a per-row report.
     */
    @Transactional
    public Map<String, Object> importRows(
            AuthPrincipal principal, String resourceKey, List<Map<String, Object>> rows) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);

        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException("The import file has no data rows");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BadRequestException("Imports are limited to " + MAX_IMPORT_ROWS + " rows at a time");
        }

        Map<String, Boolean> relationCache = new HashMap<>();
        Map<String, String> nameCache = new HashMap<>();
        List<PreparedInsert> prepared = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int index = 0; index < rows.size(); index++) {
            try {
                prepared.add(prepareInsert(
                        resource, rows.get(index), true, true, relationCache, nameCache));
            } catch (BadRequestException ex) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("row", index + 2);
                error.put("message", ex.getMessage());
                errors.add(error);
            }
        }

        if (!errors.isEmpty()) {
            return importResult(0, rows.size(), errors);
        }

        for (PreparedInsert row : prepared) {
            insert(resource, row);
        }
        return importResult(prepared.size(), rows.size(), List.of());
    }

    private static Map<String, Object> importResult(
            int imported, int total, List<Map<String, Object>> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("total", total);
        result.put("errors", errors);
        return result;
    }

    @Transactional
    public Map<String, Object> update(
            AuthPrincipal principal, String resourceKey, String id, Map<String, Object> body) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);
        validateRelations(resource, body, new HashMap<>());

        List<CrmField> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (CrmField field : resource.fields()) {
            if (body == null || !body.containsKey(field.key())) {
                continue;
            }
            Object raw = body.get(field.key());
            if (field.required() && isBlank(raw)) {
                throw new BadRequestException(field.key() + " is required");
            }
            fields.add(field);
            values.add(coerce(field, raw));
        }

        if (fields.isEmpty()) {
            return get(principal, resourceKey, id);
        }

        StringBuilder sql = new StringBuilder("update ").append(resource.table()).append(" set ");
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(fields.get(i).column()).append(" = ?");
        }
        params.addAll(values);
        sql.append(", updated_at = now() where id = ?");
        params.add(id);

        int updated = jdbc.update(sql.toString(), params.toArray());
        if (updated == 0) {
            throw new NotFoundException(resource.label() + " not found");
        }
        return get(principal, resourceKey, id);
    }

    @Transactional
    public void delete(AuthPrincipal principal, String resourceKey, String id) {
        requireWorkspace(principal);
        CrmResource resource = resource(resourceKey);
        int deleted = jdbc.update("delete from " + resource.table() + " where id = ?", id);
        if (deleted == 0) {
            throw new NotFoundException(resource.label() + " not found");
        }
    }

    /* ------------------------------- overview ------------------------------- */

    @Transactional(readOnly = true)
    public Map<String, Object> overview(AuthPrincipal principal) {
        requireWorkspace(principal);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leads", count("select count(*) from leads"));
        out.put("deals", count("select count(*) from deals"));
        out.put("companies", count("select count(*) from companies"));
        out.put("contacts", count("select count(*) from crm_contacts"));
        out.put("products", count("select count(*) from products"));
        out.put("orders", count("select count(*) from orders"));
        out.put("documents", count("select count(*) from crm_documents"));
        out.put("openTasks", count("select count(*) from tasks where status not in ('DONE', 'CANCELLED')"));
        out.put("upcomingMeetings", count(
                "select count(*) from meetings where starts_at >= now() and status = 'SCHEDULED'"));
        out.put("pipelineValue", money(
                "select coalesce(sum(value_amount), 0) from deals where stage not in ('WON', 'LOST')"));
        out.put("wonValue", money(
                "select coalesce(sum(value_amount), 0) from deals where stage = 'WON'"));
        out.put("orderValue", money(
                "select coalesce(sum(oi.quantity * oi.unit_price), 0) "
                        + "from order_items oi join orders o on o.id = oi.order_id"));
        return out;
    }

    /* ------------------------------ lead profile ----------------------------- */

    /**
     * A lead plus every record linked to it. The profile view would otherwise
     * have to fetch and filter six separate lists; doing it in one tenant-bound
     * transaction keeps it fast and consistent.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> leadProfile(AuthPrincipal principal, String leadId) {
        Map<String, Object> lead = get(principal, "leads", leadId);
        CrmResource orders = resource("orders");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lead", lead);
        out.put("deals", jdbc.query(
                "select * from deals where lead_id = ? order by created_at desc",
                rowMapper(), leadId));
        out.put("orders", jdbc.query(
                "select " + orders.selectColumns() + " " + orders.fromSql()
                        + " where t.lead_id = ? order by t.created_at desc",
                rowMapper(), leadId));
        out.put("tasks", relatedToLead("tasks", "t.created_at desc", leadId));
        out.put("calls", relatedToLead("calls", "t.occurred_at desc", leadId));
        out.put("meetings", relatedToLead("meetings", "t.starts_at desc", leadId));
        out.put("documents", relatedToLead("crm_documents", "t.created_at desc", leadId));
        return out;
    }

    private List<Map<String, Object>> relatedToLead(String table, String orderBy, String leadId) {
        // `table` and `orderBy` are internal literals, never request data.
        return jdbc.query(
                "select * from " + table + " t where t.related_type = 'LEAD' and t.related_id = ?"
                        + " order by " + orderBy,
                rowMapper(), leadId);
    }

    /* ------------------------------ order items ----------------------------- */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orderItems(AuthPrincipal principal, String orderId) {
        requireWorkspace(principal);
        requireOrder(orderId);
        return jdbc.query(
                "select * from order_items where order_id = ? order by line_no asc, id asc",
                rowMapper(), orderId);
    }

    @Transactional
    public Map<String, Object> addOrderItem(
            AuthPrincipal principal, String orderId, Map<String, Object> body) {
        requireWorkspace(principal);
        requireOrder(orderId);

        if (body == null) {
            throw new BadRequestException("An order item is required");
        }

        String productId = text(body.get("productId"));
        String productName = text(body.get("productName"));
        BigDecimal unitPrice = decimalOrNull(body.get("unitPrice"));

        if (productId != null) {
            List<Map<String, Object>> products = jdbc.query(
                    "select name, price from products where id = ?", (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("price", rs.getBigDecimal("price"));
                        return row;
                    }, productId);
            if (products.isEmpty()) {
                throw new BadRequestException("Unknown product");
            }
            if (productName == null) {
                productName = (String) products.get(0).get("name");
            }
            if (unitPrice == null) {
                unitPrice = (BigDecimal) products.get(0).get("price");
            }
        }

        if (productName == null || productName.isBlank()) {
            throw new BadRequestException("productName is required");
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }

        int quantity = intOr(body.get("quantity"), 1);
        if (quantity < 1) {
            throw new BadRequestException("quantity must be at least 1");
        }

        Integer lineNo = intOrNull(body.get("lineNo"));
        if (lineNo == null) {
            Integer max = jdbc.queryForObject(
                    "select coalesce(max(line_no), 0) from order_items where order_id = ?",
                    Integer.class, orderId);
            lineNo = (max == null ? 0 : max) + 1;
        }

        String id = Ids.uuid();
        jdbc.update("""
                insert into order_items (id, order_id, product_id, product_name, quantity, unit_price, line_no)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, orderId, productId, productName, quantity, unitPrice, lineNo);

        return jdbc.queryForObject(
                "select * from order_items where id = ?", rowMapper(), id);
    }

    @Transactional
    public void deleteOrderItem(AuthPrincipal principal, String orderId, String itemId) {
        requireWorkspace(principal);
        int deleted = jdbc.update(
                "delete from order_items where id = ? and order_id = ?", itemId, orderId);
        if (deleted == 0) {
            throw new NotFoundException("Order item not found");
        }
    }

    /* -------------------------------- helpers ------------------------------- */

    private void requireOrder(String orderId) {
        Long found = jdbc.queryForObject(
                "select count(*) from orders where id = ?", Long.class, orderId);
        if (found == null || found == 0) {
            throw new NotFoundException("Order not found");
        }
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal money(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private CrmResource resource(String key) {
        CrmResource resource = CrmResource.ALL.get(key);
        if (resource == null) {
            throw new NotFoundException("Unknown CRM resource: " + key);
        }
        return resource;
    }

    private PreparedInsert prepareInsert(
            CrmResource resource,
            Map<String, Object> body,
            boolean skipBlankOptional,
            boolean resolveNames,
            Map<String, Boolean> relationCache,
            Map<String, String> nameCache) {
        Map<String, Object> values = resolveNames
                ? resolveRelationNames(resource, body, nameCache)
                : body;
        validateRelations(resource, values, relationCache);

        List<CrmField> fields = new ArrayList<>();
        List<Object> coerced = new ArrayList<>();
        for (CrmField field : resource.fields()) {
            boolean present = values != null && values.containsKey(field.key());
            if (!present) {
                if (field.required()) {
                    throw new BadRequestException(field.key() + " is required");
                }
                continue;
            }
            Object raw = values.get(field.key());
            if (field.required() && isBlank(raw)) {
                throw new BadRequestException(field.key() + " is required");
            }
            if (skipBlankOptional && !field.required() && isBlank(raw)) {
                continue;
            }
            fields.add(field);
            coerced.add(coerce(field, raw));
        }
        return new PreparedInsert(fields, coerced);
    }

    private String insert(CrmResource resource, PreparedInsert prepared) {
        String id = Ids.uuid();
        StringBuilder columns = new StringBuilder("id");
        StringBuilder placeholders = new StringBuilder("?");
        List<Object> params = new ArrayList<>();
        params.add(id);
        for (CrmField field : prepared.fields()) {
            columns.append(", ").append(field.column());
            placeholders.append(", ?");
        }
        params.addAll(prepared.values());

        jdbc.update("insert into " + resource.table() + " (" + columns + ") values (" + placeholders + ")",
                params.toArray());
        return id;
    }

    /**
     * CSV files are friendlier with names ("Acme Inc.") than UUIDs. Resolve
     * those names inside the tenant before the normal ID validation runs.
     */
    private Map<String, Object> resolveRelationNames(
            CrmResource resource, Map<String, Object> body, Map<String, String> cache) {
        if (body == null) {
            return null;
        }
        Map<String, Object> resolved = new LinkedHashMap<>(body);
        switch (resource.key()) {
            case "leads", "deals", "contacts", "orders" -> {
                resolveNameField(resolved, "companyId", "companies", "name", cache);
                resolveNameField(resolved, "leadId", "leads", "name", cache);
            }
            case "tasks", "calls", "meetings", "documents" -> {
                String type = text(resolved.get("relatedType"));
                String id = text(resolved.get("relatedId"));
                if (id == null) {
                    return resolved;
                }
                boolean isId = UUID_PATTERN.matcher(id).matches();
                if (type == null) {
                    if (!isId) {
                        throw new BadRequestException(
                                "relatedType is required when relatedId is not a record id");
                    }
                    return resolved;
                }
                if (!isId) {
                    String key = type.toUpperCase(Locale.ROOT);
                    String table = RELATED_TABLES.get(key);
                    String nameExpression = RELATED_NAME_EXPRESSIONS.get(key);
                    if (table == null || nameExpression == null) {
                        throw new BadRequestException(
                                "relatedType does not support names for relatedId");
                    }
                    resolved.put("relatedId", resolveByName(
                            table, nameExpression, id, "relatedId", cache));
                }
            }
            default -> {
            }
        }
        return resolved;
    }

    private void resolveNameField(
            Map<String, Object> body,
            String key,
            String table,
            String nameExpression,
            Map<String, String> cache) {
        String value = text(body.get(key));
        if (value == null || UUID_PATTERN.matcher(value).matches()) {
            return;
        }
        body.put(key, resolveByName(table, nameExpression, value, key, cache));
    }

    private String resolveByName(
            String table,
            String nameExpression,
            String value,
            String field,
            Map<String, String> cache) {
        String cacheKey = table + "|" + value.toLowerCase(Locale.ROOT);
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> ids = jdbc.query(
                "select id from " + table + " where lower(" + nameExpression + ") = lower(?)"
                        + " order by id limit 2",
                (rs, rowNum) -> rs.getString(1), value);
        if (ids.isEmpty()) {
            throw new BadRequestException(field + " does not match a record in this workspace");
        }
        if (ids.size() > 1) {
            throw new BadRequestException(
                    field + " matches more than one record; use the record id instead");
        }
        cache.put(cacheKey, ids.get(0));
        return ids.get(0);
    }

    /**
     * Keeps a link from pointing at another workspace's record. Foreign keys
     * alone cannot enforce this because PostgreSQL checks them outside RLS.
     */
    private void validateRelations(
            CrmResource resource, Map<String, Object> body, Map<String, Boolean> cache) {
        if (body == null) {
            return;
        }
        switch (resource.key()) {
            case "leads", "deals", "contacts", "orders" -> {
                validateReference(body.get("companyId"), "companies", "companyId", cache);
                validateReference(body.get("leadId"), "leads", "leadId", cache);
            }
            case "tasks", "calls", "meetings", "documents" -> {
                String type = text(body.get("relatedType"));
                String id = text(body.get("relatedId"));
                if (type != null && id != null) {
                    String table = RELATED_TABLES.get(type.toUpperCase(Locale.ROOT));
                    if (table != null) {
                        validateReference(id, table, "relatedId", cache);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void validateReference(
            Object raw, String table, String field, Map<String, Boolean> cache) {
        String id = text(raw);
        if (id == null) {
            return;
        }
        String cacheKey = table + ":" + id;
        if (cache.containsKey(cacheKey)) {
            return;
        }
        Long found = jdbc.queryForObject(
                "select count(*) from " + table + " where id = ?", Long.class, id);
        if (found == null || found == 0) {
            throw new BadRequestException(field + " does not match a record in this workspace");
        }
        cache.put(cacheKey, Boolean.TRUE);
    }

    private record PreparedInsert(List<CrmField> fields, List<Object> values) {
    }

    private Object coerce(CrmField field, Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        try {
            return switch (field.type()) {
                case TEXT -> text.isEmpty() ? null : text;
                case INT -> integer(raw, text);
                case DECIMAL -> new BigDecimal(text);
                case BOOL -> booleanValue(raw, text);
                case DATE -> LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
                case TIMESTAMP -> timestamp(text);
            };
        } catch (NumberFormatException | DateTimeParseException ex) {
            throw new BadRequestException("Invalid value for " + field.key());
        }
    }

    private static boolean booleanValue(Object raw, String text) {
        if (raw instanceof Boolean value) {
            return value;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw new BadRequestException("Invalid boolean value");
        };
    }

    private static Object integer(Object raw, String text) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(text);
    }

    private static Timestamp timestamp(String text) {
        String value = text.contains(" ") && !text.contains("T") ? text.replace(' ', 'T') : text;
        try {
            if (value.endsWith("Z")) {
                return Timestamp.from(Instant.parse(value));
            }
            if (value.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return Timestamp.from(OffsetDateTime.parse(value).toInstant());
            }
            if (value.length() == 10) {
                return Timestamp.valueOf(LocalDate.parse(value).atStartOfDay());
            }
            return Timestamp.valueOf(LocalDateTime.parse(value));
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid date/time");
        }
    }

    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    private static int intOr(Object value, int fallback) {
        Integer parsed = intOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private void requireWorkspace(AuthPrincipal principal) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }
    }

    private static RowMapper<Map<String, Object>> rowMapper() {
        return (rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String label = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value instanceof Timestamp ts) {
                    value = ts.toInstant().toString();
                } else if (value instanceof java.sql.Date date) {
                    value = date.toLocalDate().toString();
                } else if (value instanceof OffsetDateTime offsetDateTime) {
                    value = offsetDateTime.toInstant().toString();
                } else if (value instanceof LocalDateTime localDateTime) {
                    value = localDateTime.toString();
                }
                row.put(camel(label), value);
            }
            return row;
        };
    }

    private static String camel(String label) {
        StringBuilder out = new StringBuilder(label.length());
        boolean upper = false;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch == '_') {
                upper = true;
                continue;
            }
            out.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return out.toString();
    }
}
