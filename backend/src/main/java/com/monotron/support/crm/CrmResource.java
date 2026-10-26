package com.monotron.support.crm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative description of one CRM entity. The service uses these
 * descriptors to build safe, whitelisted SQL — user supplied field names are
 * never interpolated into a statement.
 */
public record CrmResource(
        String key,
        String table,
        String label,
        List<CrmField> fields,
        List<String> searchColumns,
        String selectColumns,
        String fromSql,
        String orderBy) {

    public static final Map<String, CrmResource> ALL = build();

    public CrmField field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }

    private static Map<String, CrmResource> build() {
        Map<String, CrmResource> map = new LinkedHashMap<>();

        map.put("leads", new CrmResource(
                "leads", "leads", "Lead",
                List.of(
                        CrmField.required("name", "name"),
                        CrmField.text("firstName", "first_name"),
                        CrmField.text("lastName", "last_name"),
                        CrmField.text("title", "title"),
                        CrmField.text("email", "email"),
                        CrmField.text("phone", "phone"),
                        CrmField.text("whatsapp", "whatsapp"),
                        CrmField.text("website", "website"),
                        CrmField.text("location", "location"),
                        CrmField.text("country", "country"),
                        CrmField.text("industry", "industry"),
                        CrmField.text("companyText", "company_text"),
                        CrmField.text("companyId", "company_id"),
                        CrmField.text("source", "source"),
                        CrmField.text("status", "status"),
                        CrmField.text("priority", "priority"),
                        CrmField.text("buyingIntent", "buying_intent"),
                        CrmField.date("expectedPurchaseDate", "expected_purchase_date"),
                        CrmField.integer("leadScore", "lead_score"),
                        CrmField.text("notes", "notes")),
                List.of(
                        "lower(coalesce(t.name, '')) like ?",
                        "lower(coalesce(t.email, '')) like ?",
                        "lower(coalesce(t.company_text, '')) like ?"),
                "t.*, c.name as company_name",
                "from leads t left join companies c on c.id = t.company_id",
                "t.created_at desc"));

        map.put("deals", new CrmResource(
                "deals", "deals", "Deal",
                List.of(
                        CrmField.required("title", "title"),
                        CrmField.decimal("valueAmount", "value_amount"),
                        CrmField.text("stage", "stage"),
                        CrmField.text("companyId", "company_id"),
                        CrmField.text("leadId", "lead_id"),
                        CrmField.date("expectedCloseDate", "expected_close_date"),
                        CrmField.text("notes", "notes")),
                List.of("lower(coalesce(t.title, '')) like ?"),
                "t.*, c.name as company_name, l.name as lead_name",
                "from deals t left join companies c on c.id = t.company_id left join leads l on l.id = t.lead_id",
                "t.created_at desc"));

        map.put("companies", new CrmResource(
                "companies", "companies", "Company",
                List.of(
                        CrmField.required("name", "name"),
                        CrmField.text("domain", "domain"),
                        CrmField.text("industry", "industry"),
                        CrmField.text("size", "size"),
                        CrmField.text("notes", "notes")),
                List.of(
                        "lower(coalesce(t.name, '')) like ?",
                        "lower(coalesce(t.domain, '')) like ?"),
                "t.*",
                "from companies t",
                "lower(t.name) asc"));

        map.put("contacts", new CrmResource(
                "contacts", "crm_contacts", "Contact",
                List.of(
                        CrmField.required("firstName", "first_name"),
                        CrmField.text("lastName", "last_name"),
                        CrmField.text("email", "email"),
                        CrmField.text("phone", "phone"),
                        CrmField.text("title", "title"),
                        CrmField.text("companyId", "company_id"),
                        CrmField.text("status", "status"),
                        CrmField.text("notes", "notes")),
                List.of(
                        "lower(coalesce(t.first_name, '')) like ?",
                        "lower(coalesce(t.last_name, '')) like ?",
                        "lower(coalesce(t.email, '')) like ?"),
                "t.*, c.name as company_name",
                "from crm_contacts t left join companies c on c.id = t.company_id",
                "t.created_at desc"));

        map.put("tasks", new CrmResource(
                "tasks", "tasks", "Task",
                List.of(
                        CrmField.required("title", "title"),
                        CrmField.text("notes", "notes"),
                        CrmField.date("dueDate", "due_date"),
                        CrmField.text("priority", "priority"),
                        CrmField.text("status", "status"),
                        CrmField.text("relatedType", "related_type"),
                        CrmField.text("relatedId", "related_id")),
                List.of("lower(coalesce(t.title, '')) like ?"),
                "t.*",
                "from tasks t",
                "t.created_at desc"));

        map.put("calls", new CrmResource(
                "calls", "calls", "Call",
                List.of(
                        CrmField.text("direction", "direction"),
                        CrmField.text("outcome", "outcome"),
                        CrmField.text("subject", "subject"),
                        CrmField.text("notes", "notes"),
                        CrmField.integer("durationSeconds", "duration_seconds"),
                        CrmField.text("relatedType", "related_type"),
                        CrmField.text("relatedId", "related_id"),
                        CrmField.timestamp("occurredAt", "occurred_at")),
                List.of("lower(coalesce(t.subject, '')) like ?"),
                "t.*",
                "from calls t",
                "t.occurred_at desc"));

        map.put("meetings", new CrmResource(
                "meetings", "meetings", "Meeting",
                List.of(
                        CrmField.required("title", "title"),
                        CrmField.requiredTimestamp("startsAt", "starts_at"),
                        CrmField.integer("durationMinutes", "duration_minutes"),
                        CrmField.text("location", "location"),
                        CrmField.text("status", "status"),
                        CrmField.text("relatedType", "related_type"),
                        CrmField.text("relatedId", "related_id"),
                        CrmField.text("notes", "notes")),
                List.of("lower(coalesce(t.title, '')) like ?"),
                "t.*",
                "from meetings t",
                "t.starts_at desc"));

        map.put("products", new CrmResource(
                "products", "products", "Product",
                List.of(
                        CrmField.required("name", "name"),
                        CrmField.text("sku", "sku"),
                        CrmField.decimal("price", "price"),
                        CrmField.text("description", "description"),
                        CrmField.bool("active", "active")),
                List.of(
                        "lower(coalesce(t.name, '')) like ?",
                        "lower(coalesce(t.sku, '')) like ?"),
                "t.*",
                "from products t",
                "lower(t.name) asc"));

        map.put("orders", new CrmResource(
                "orders", "orders", "Order",
                List.of(
                        CrmField.required("orderNumber", "order_number"),
                        CrmField.text("status", "status"),
                        CrmField.date("orderDate", "order_date"),
                        CrmField.text("companyId", "company_id"),
                        CrmField.text("leadId", "lead_id"),
                        CrmField.text("notes", "notes")),
                List.of(
                        "lower(coalesce(t.order_number, '')) like ?",
                        "lower(coalesce(c.name, '')) like ?"),
                "t.*, c.name as company_name, l.name as lead_name, "
                        + "(select count(*) from order_items oi where oi.order_id = t.id) as item_count, "
                        + "(select coalesce(sum(oi.quantity * oi.unit_price), 0) from order_items oi where oi.order_id = t.id) as total_amount",
                "from orders t left join companies c on c.id = t.company_id left join leads l on l.id = t.lead_id",
                "t.created_at desc"));

        map.put("documents", new CrmResource(
                "documents", "crm_documents", "Document",
                List.of(
                        CrmField.required("name", "name"),
                        CrmField.text("category", "category"),
                        CrmField.text("url", "url"),
                        CrmField.text("relatedType", "related_type"),
                        CrmField.text("relatedId", "related_id"),
                        CrmField.text("notes", "notes")),
                List.of(
                        "lower(coalesce(t.name, '')) like ?",
                        "lower(coalesce(t.category, '')) like ?"),
                "t.*",
                "from crm_documents t",
                "t.created_at desc"));

        return Map.copyOf(map);
    }
}
