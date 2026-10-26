-- =====================================================================
-- RelayDesk - V8: CRM contacts and documents
--
-- The baseline schema already ships leads, deals, companies, tasks,
-- calls, meetings, products, orders and order_items. These two tables
-- complete the CRM by giving it its own contact book (separate from the
-- widget visitor directory in `contacts`) and a document register
-- (separate from knowledge-base uploads in `documents`).
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.crm_contacts (
    id character varying(36) PRIMARY KEY,
    first_name character varying(150) NOT NULL,
    last_name character varying(150),
    email character varying(255),
    phone character varying(50),
    title character varying(150),
    company_id character varying(36),
    status character varying(16) NOT NULL DEFAULT 'ACTIVE',
    notes text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying)
);

ALTER TABLE public.crm_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_contacts FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.crm_contacts;
CREATE POLICY tenant_isolation ON public.crm_contacts
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_crm_contacts_workspace ON public.crm_contacts;
CREATE TRIGGER trg_crm_contacts_workspace BEFORE INSERT ON public.crm_contacts
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_crm_contacts_workspace
    ON public.crm_contacts (workspace_id);
CREATE INDEX IF NOT EXISTS idx_crm_contacts_company
    ON public.crm_contacts (company_id);

CREATE TABLE IF NOT EXISTS public.crm_documents (
    id character varying(36) PRIMARY KEY,
    name character varying(300) NOT NULL,
    category character varying(100),
    url character varying(1000),
    related_type character varying(16),
    related_id character varying(36),
    notes text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying)
);

ALTER TABLE public.crm_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_documents FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.crm_documents;
CREATE POLICY tenant_isolation ON public.crm_documents
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_crm_documents_workspace ON public.crm_documents;
CREATE TRIGGER trg_crm_documents_workspace BEFORE INSERT ON public.crm_documents
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_crm_documents_workspace
    ON public.crm_documents (workspace_id);
