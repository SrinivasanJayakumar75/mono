/**
 * Declarative description of every CRM module.
 *
 * The same configuration drives the sidebar, the list table, the search
 * placeholder and the create/edit form, so adding a field is a one-line change.
 */

export type CrmKey =
  | "leads"
  | "deals"
  | "companies"
  | "contacts"
  | "tasks"
  | "calls"
  | "meetings"
  | "products"
  | "orders"
  | "documents";

export type CrmTab = "overview" | CrmKey;

export type CrmRecord = Record<string, unknown>;

export type CrmTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

export type CrmFieldType =
  | "text"
  | "email"
  | "tel"
  | "url"
  | "number"
  | "money"
  | "date"
  | "datetime"
  | "textarea"
  | "select"
  | "relation"
  | "checkbox";

export interface CrmFieldConfig {
  key: string;
  label: string;
  type?: CrmFieldType;
  required?: boolean;
  options?: { value: string; label: string }[];
  relation?: "companies" | "leads" | "products";
  placeholder?: string;
  hint?: string;
  full?: boolean;
  defaultValue?: string | boolean;
}

export type CrmColumnKind = "text" | "badge" | "money" | "date" | "datetime" | "number" | "bool";

export interface CrmColumnConfig {
  key: string;
  label: string;
  kind?: CrmColumnKind;
  tone?: Record<string, CrmTone>;
  width?: string;
}

export interface CrmResourceConfig {
  key: CrmKey;
  label: string;
  singular: string;
  description: string;
  emptyDescription: string;
  columns: CrmColumnConfig[];
  fields: CrmFieldConfig[];
  /** Field keys exposed as filters. Select, relation and checkbox fields render as dropdowns. */
  filters?: string[];
}

const PRIORITY_TONES: Record<string, CrmTone> = {
  LOW: "neutral",
  NORMAL: "neutral",
  MEDIUM: "info",
  HIGH: "warning",
  URGENT: "danger",
};

const LEAD_STATUS_TONES: Record<string, CrmTone> = {
  NEW: "info",
  CONTACTED: "brand",
  QUALIFIED: "brand",
  PROPOSAL: "warning",
  NEGOTIATION: "warning",
  WON: "success",
  LOST: "danger",
  NURTURE: "neutral",
};

const DEAL_STAGE_TONES: Record<string, CrmTone> = {
  NEW: "info",
  QUALIFIED: "brand",
  PROPOSAL: "warning",
  NEGOTIATION: "warning",
  WON: "success",
  LOST: "danger",
};

const TASK_STATUS_TONES: Record<string, CrmTone> = {
  OPEN: "warning",
  IN_PROGRESS: "info",
  DONE: "success",
  CANCELLED: "neutral",
};

const ORDER_STATUS_TONES: Record<string, CrmTone> = {
  PENDING: "warning",
  CONFIRMED: "info",
  SHIPPED: "brand",
  DELIVERED: "success",
  CANCELLED: "danger",
};

const MEETING_STATUS_TONES: Record<string, CrmTone> = {
  SCHEDULED: "info",
  COMPLETED: "success",
  CANCELLED: "neutral",
};

const CALL_OUTCOME_TONES: Record<string, CrmTone> = {
  CONNECTED: "success",
  NO_ANSWER: "warning",
  BUSY: "neutral",
  LEFT_MESSAGE: "info",
  FAILED: "danger",
};

const CALL_DIRECTION_TONES: Record<string, CrmTone> = {
  INBOUND: "info",
  OUTBOUND: "brand",
};

const CONTACT_STATUS_TONES: Record<string, CrmTone> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
};

const RELATED_TYPE_OPTIONS = [
  { value: "LEAD", label: "Lead" },
  { value: "DEAL", label: "Deal" },
  { value: "COMPANY", label: "Company" },
  { value: "CONTACT", label: "Contact" },
  { value: "ORDER", label: "Order" },
];

const PRIORITY_OPTIONS = [
  { value: "LOW", label: "Low" },
  { value: "NORMAL", label: "Normal" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
  { value: "URGENT", label: "Urgent" },
];

export const CRM_RESOURCES: Record<CrmKey, CrmResourceConfig> = {
  leads: {
    key: "leads",
    label: "Leads",
    singular: "Lead",
    description: "People and companies that have shown interest in your product.",
    emptyDescription: "Capture a lead to start tracking its journey through the pipeline.",
    columns: [
      { key: "name", label: "Lead", width: "min-w-[180px]" },
      { key: "companyText", label: "Company" },
      { key: "email", label: "Email" },
      { key: "status", label: "Status", kind: "badge", tone: LEAD_STATUS_TONES },
      { key: "priority", label: "Priority", kind: "badge", tone: PRIORITY_TONES },
      { key: "createdAt", label: "Added", kind: "date" },
    ],
    fields: [
      { key: "name", label: "Full name", required: true, placeholder: "Priya Sharma" },
      { key: "firstName", label: "First name" },
      { key: "lastName", label: "Last name" },
      { key: "title", label: "Job title" },
      { key: "email", label: "Email", type: "email" },
      { key: "phone", label: "Phone", type: "tel" },
      { key: "whatsapp", label: "WhatsApp", type: "tel" },
      { key: "website", label: "Website", type: "url" },
      { key: "companyText", label: "Company name" },
      { key: "companyId", label: "Linked company", type: "relation", relation: "companies" },
      {
        key: "source",
        label: "Source",
        type: "select",
        options: [
          { value: "WEBSITE", label: "Website" },
          { value: "REFERRAL", label: "Referral" },
          { value: "CAMPAIGN", label: "Campaign" },
          { value: "OUTBOUND", label: "Outbound" },
          { value: "EVENT", label: "Event" },
          { value: "OTHER", label: "Other" },
        ],
      },
      {
        key: "status",
        label: "Status",
        type: "select",
        options: [
          { value: "NEW", label: "New" },
          { value: "CONTACTED", label: "Contacted" },
          { value: "QUALIFIED", label: "Qualified" },
          { value: "PROPOSAL", label: "Proposal sent" },
          { value: "NEGOTIATION", label: "Negotiation" },
          { value: "WON", label: "Won" },
          { value: "LOST", label: "Lost" },
          { value: "NURTURE", label: "Nurture" },
        ],
      },
      { key: "priority", label: "Priority", type: "select", options: PRIORITY_OPTIONS },
      {
        key: "buyingIntent",
        label: "Buying intent",
        type: "select",
        options: [
          { value: "LOW", label: "Low" },
          { value: "MEDIUM", label: "Medium" },
          { value: "HIGH", label: "High" },
        ],
      },
      { key: "industry", label: "Industry" },
      { key: "location", label: "Location" },
      { key: "country", label: "Country" },
      { key: "leadScore", label: "Lead score", type: "number" },
      { key: "expectedPurchaseDate", label: "Expected purchase", type: "date" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["status", "priority", "source", "buyingIntent", "companyId"],
  },

  deals: {
    key: "deals",
    label: "Deals",
    singular: "Deal",
    description: "Revenue opportunities moving through your sales stages.",
    emptyDescription: "Create a deal to track value, stage and expected close date.",
    columns: [
      { key: "title", label: "Deal", width: "min-w-[200px]" },
      { key: "companyName", label: "Company" },
      { key: "valueAmount", label: "Value", kind: "money" },
      { key: "stage", label: "Stage", kind: "badge", tone: DEAL_STAGE_TONES },
      { key: "expectedCloseDate", label: "Close date", kind: "date" },
      { key: "createdAt", label: "Created", kind: "date" },
    ],
    fields: [
      { key: "title", label: "Deal name", required: true, placeholder: "Acme — annual plan", full: true },
      { key: "valueAmount", label: "Value", type: "money" },
      {
        key: "stage",
        label: "Stage",
        type: "select",
        options: [
          { value: "NEW", label: "New" },
          { value: "QUALIFIED", label: "Qualified" },
          { value: "PROPOSAL", label: "Proposal" },
          { value: "NEGOTIATION", label: "Negotiation" },
          { value: "WON", label: "Won" },
          { value: "LOST", label: "Lost" },
        ],
      },
      { key: "companyId", label: "Company", type: "relation", relation: "companies" },
      { key: "leadId", label: "Lead", type: "relation", relation: "leads" },
      { key: "expectedCloseDate", label: "Expected close", type: "date" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["companyId", "leadId"],
  },

  companies: {
    key: "companies",
    label: "Companies",
    singular: "Company",
    description: "Accounts you sell to, with their industry and size.",
    emptyDescription: "Add a company to connect its leads, contacts and deals.",
    columns: [
      { key: "name", label: "Company", width: "min-w-[200px]" },
      { key: "domain", label: "Domain" },
      { key: "industry", label: "Industry" },
      { key: "size", label: "Size" },
      { key: "createdAt", label: "Added", kind: "date" },
    ],
    fields: [
      { key: "name", label: "Company name", required: true, placeholder: "Acme Inc." },
      { key: "domain", label: "Website", placeholder: "acme.com" },
      { key: "industry", label: "Industry" },
      {
        key: "size",
        label: "Company size",
        type: "select",
        options: [
          { value: "1-10", label: "1–10" },
          { value: "11-50", label: "11–50" },
          { value: "51-200", label: "51–200" },
          { value: "201-500", label: "201–500" },
          { value: "501-1000", label: "501–1000" },
          { value: "1000+", label: "1000+" },
        ],
      },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["size"],
  },

  contacts: {
    key: "contacts",
    label: "Contacts",
    singular: "Contact",
    description: "Your CRM address book of people at customer accounts.",
    emptyDescription: "Add a contact so every deal and activity has a face attached.",
    columns: [
      { key: "firstName", label: "First name", width: "min-w-[140px]" },
      { key: "lastName", label: "Last name" },
      { key: "companyName", label: "Company" },
      { key: "email", label: "Email" },
      { key: "phone", label: "Phone" },
      { key: "status", label: "Status", kind: "badge", tone: CONTACT_STATUS_TONES },
    ],
    fields: [
      { key: "firstName", label: "First name", required: true },
      { key: "lastName", label: "Last name" },
      { key: "email", label: "Email", type: "email" },
      { key: "phone", label: "Phone", type: "tel" },
      { key: "title", label: "Job title" },
      { key: "companyId", label: "Company", type: "relation", relation: "companies" },
      {
        key: "status",
        label: "Status",
        type: "select",
        options: [
          { value: "ACTIVE", label: "Active" },
          { value: "INACTIVE", label: "Inactive" },
        ],
      },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["status", "companyId"],
  },

  tasks: {
    key: "tasks",
    label: "Tasks",
    singular: "Task",
    description: "Follow-ups, reminders and to-dos for the team.",
    emptyDescription: "Add a task to make sure the next step is never forgotten.",
    columns: [
      { key: "title", label: "Task", width: "min-w-[220px]" },
      { key: "status", label: "Status", kind: "badge", tone: TASK_STATUS_TONES },
      { key: "priority", label: "Priority", kind: "badge", tone: PRIORITY_TONES },
      { key: "dueDate", label: "Due", kind: "date" },
      { key: "relatedType", label: "Related to" },
      { key: "createdAt", label: "Created", kind: "date" },
    ],
    fields: [
      { key: "title", label: "Task", required: true, placeholder: "Send the proposal", full: true },
      { key: "dueDate", label: "Due date", type: "date" },
      {
        key: "priority",
        label: "Priority",
        type: "select",
        options: PRIORITY_OPTIONS,
      },
      {
        key: "status",
        label: "Status",
        type: "select",
        options: [
          { value: "OPEN", label: "Open" },
          { value: "IN_PROGRESS", label: "In progress" },
          { value: "DONE", label: "Done" },
          { value: "CANCELLED", label: "Cancelled" },
        ],
      },
      { key: "relatedType", label: "Related to", type: "select", options: RELATED_TYPE_OPTIONS },
      { key: "relatedId", label: "Related record id" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["status", "priority", "relatedType"],
  },

  calls: {
    key: "calls",
    label: "Calls",
    singular: "Call",
    description: "A log of every conversation the team has had on the phone.",
    emptyDescription: "Log a call to keep a full history of customer conversations.",
    columns: [
      { key: "subject", label: "Call", width: "min-w-[200px]" },
      { key: "direction", label: "Direction", kind: "badge", tone: CALL_DIRECTION_TONES },
      { key: "outcome", label: "Outcome", kind: "badge", tone: CALL_OUTCOME_TONES },
      { key: "durationSeconds", label: "Duration (s)", kind: "number" },
      { key: "occurredAt", label: "When", kind: "datetime" },
    ],
    fields: [
      { key: "subject", label: "Subject", placeholder: "Discovery call", full: true },
      {
        key: "direction",
        label: "Direction",
        type: "select",
        options: [
          { value: "OUTBOUND", label: "Outbound" },
          { value: "INBOUND", label: "Inbound" },
        ],
      },
      {
        key: "outcome",
        label: "Outcome",
        type: "select",
        options: [
          { value: "CONNECTED", label: "Connected" },
          { value: "NO_ANSWER", label: "No answer" },
          { value: "BUSY", label: "Busy" },
          { value: "LEFT_MESSAGE", label: "Left a message" },
          { value: "FAILED", label: "Failed" },
        ],
      },
      { key: "durationSeconds", label: "Duration (seconds)", type: "number" },
      { key: "occurredAt", label: "When", type: "datetime" },
      { key: "relatedType", label: "Related to", type: "select", options: RELATED_TYPE_OPTIONS },
      { key: "relatedId", label: "Related record id" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["direction", "outcome", "relatedType"],
  },

  meetings: {
    key: "meetings",
    label: "Meetings",
    singular: "Meeting",
    description: "Demo and check-in schedule, all in one place.",
    emptyDescription: "Schedule a meeting to keep the calendar and team aligned.",
    columns: [
      { key: "title", label: "Meeting", width: "min-w-[200px]" },
      { key: "startsAt", label: "Starts", kind: "datetime" },
      { key: "durationMinutes", label: "Minutes", kind: "number" },
      { key: "location", label: "Location" },
      { key: "status", label: "Status", kind: "badge", tone: MEETING_STATUS_TONES },
    ],
    fields: [
      { key: "title", label: "Meeting title", required: true, placeholder: "Product demo", full: true },
      { key: "startsAt", label: "Starts at", type: "datetime", required: true },
      { key: "durationMinutes", label: "Duration (minutes)", type: "number" },
      { key: "location", label: "Location or link" },
      {
        key: "status",
        label: "Status",
        type: "select",
        options: [
          { value: "SCHEDULED", label: "Scheduled" },
          { value: "COMPLETED", label: "Completed" },
          { value: "CANCELLED", label: "Cancelled" },
        ],
      },
      { key: "relatedType", label: "Related to", type: "select", options: RELATED_TYPE_OPTIONS },
      { key: "relatedId", label: "Related record id" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["status", "relatedType"],
  },

  products: {
    key: "products",
    label: "Products",
    singular: "Product",
    description: "The catalogue used to build quotes and orders.",
    emptyDescription: "Add a product so it can be added to orders in one click.",
    columns: [
      { key: "name", label: "Product", width: "min-w-[200px]" },
      { key: "sku", label: "SKU" },
      { key: "price", label: "Price", kind: "money" },
      { key: "active", label: "Active", kind: "bool" },
      { key: "createdAt", label: "Added", kind: "date" },
    ],
    fields: [
      { key: "name", label: "Product name", required: true },
      { key: "sku", label: "SKU" },
      { key: "price", label: "Price", type: "money" },
      { key: "active", label: "Active", type: "checkbox", defaultValue: true },
      { key: "description", label: "Description", type: "textarea", full: true },
    ],
    filters: ["active"],
  },

  orders: {
    key: "orders",
    label: "Orders",
    singular: "Order",
    description: "Confirmed purchases and their line items.",
    emptyDescription: "Create an order and add products to start billing a customer.",
    columns: [
      { key: "orderNumber", label: "Order", width: "min-w-[140px]" },
      { key: "companyName", label: "Company" },
      { key: "status", label: "Status", kind: "badge", tone: ORDER_STATUS_TONES },
      { key: "orderDate", label: "Order date", kind: "date" },
      { key: "itemCount", label: "Items", kind: "number" },
      { key: "totalAmount", label: "Total", kind: "money" },
    ],
    fields: [
      { key: "orderNumber", label: "Order number", required: true, placeholder: "ORD-1001" },
      {
        key: "status",
        label: "Status",
        type: "select",
        options: [
          { value: "PENDING", label: "Pending" },
          { value: "CONFIRMED", label: "Confirmed" },
          { value: "SHIPPED", label: "Shipped" },
          { value: "DELIVERED", label: "Delivered" },
          { value: "CANCELLED", label: "Cancelled" },
        ],
      },
      { key: "orderDate", label: "Order date", type: "date" },
      { key: "companyId", label: "Company", type: "relation", relation: "companies" },
      { key: "leadId", label: "Lead", type: "relation", relation: "leads" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["status", "companyId", "leadId"],
  },

  documents: {
    key: "documents",
    label: "Documents",
    singular: "Document",
    description: "Contracts, proposals and invoices linked to CRM records.",
    emptyDescription: "Link a contract or proposal so the whole team can find it.",
    columns: [
      { key: "name", label: "Document", width: "min-w-[220px]" },
      { key: "category", label: "Category" },
      { key: "url", label: "Link" },
      { key: "relatedType", label: "Related to" },
      { key: "createdAt", label: "Added", kind: "date" },
    ],
    fields: [
      { key: "name", label: "Document name", required: true, placeholder: "Acme — signed contract" },
      {
        key: "category",
        label: "Category",
        type: "select",
        options: [
          { value: "CONTRACT", label: "Contract" },
          { value: "PROPOSAL", label: "Proposal" },
          { value: "INVOICE", label: "Invoice" },
          { value: "QUOTE", label: "Quote" },
          { value: "OTHER", label: "Other" },
        ],
      },
      { key: "url", label: "Link", type: "url", placeholder: "https://…" },
      { key: "relatedType", label: "Related to", type: "select", options: RELATED_TYPE_OPTIONS },
      { key: "relatedId", label: "Related record id" },
      { key: "notes", label: "Notes", type: "textarea", full: true },
    ],
    filters: ["category", "relatedType"],
  },
};

export const CRM_GROUPS: { label: string; items: CrmKey[] }[] = [
  { label: "Pipeline", items: ["leads", "deals", "companies", "contacts"] },
  { label: "Activity", items: ["tasks", "calls", "meetings"] },
  { label: "Commerce", items: ["products", "orders", "documents"] },
];

export function asText(value: unknown): string {
  if (value === null || value === undefined) return "";
  return String(value);
}

export function asNumber(value: unknown): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function asBool(value: unknown): boolean {
  return value === true || value === "true" || value === 1;
}
