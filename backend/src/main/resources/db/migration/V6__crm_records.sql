CREATE TABLE public.crm_records (
    id character varying(36) PRIMARY KEY,
    module character varying(24) NOT NULL,
    record_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    workspace_id character varying(36) NOT NULL DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying)
);

ALTER TABLE public.crm_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON public.crm_records
    USING ((workspace_id)::text = (public.app_workspace_id())::text)
    WITH CHECK ((workspace_id)::text = (public.app_workspace_id())::text);
CREATE INDEX crm_records_workspace_module_updated_idx ON public.crm_records (workspace_id, module, updated_at DESC);
