-- =====================================================================
-- Monotron - V12: widget help center content
--
-- Blogs, FAQs and news announcements are authored in workspace settings
-- and published in the public messenger widget. They are intentionally
-- separate from the knowledge-base `articles` table: help-center content
-- is shown to visitors as-is and is not indexed into the AI corpus.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.widget_content (
    id character varying(36) PRIMARY KEY,
    app_id character varying(32) NOT NULL REFERENCES public.apps(id) ON DELETE CASCADE,
    kind character varying(16) NOT NULL,
    title character varying(300) NOT NULL,
    summary character varying(500) NOT NULL DEFAULT '',
    body text NOT NULL,
    published boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying),
    CONSTRAINT widget_content_kind_check
        CHECK (kind IN ('BLOG', 'FAQ', 'ANNOUNCEMENT'))
);

ALTER TABLE public.widget_content ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.widget_content FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.widget_content;
CREATE POLICY tenant_isolation ON public.widget_content
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_widget_content_workspace ON public.widget_content;
CREATE TRIGGER trg_widget_content_workspace BEFORE INSERT ON public.widget_content
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_widget_content_workspace
    ON public.widget_content (workspace_id);
CREATE INDEX IF NOT EXISTS idx_widget_content_app
    ON public.widget_content (app_id, published, kind);

-- The runtime role normally inherits new tables through the default
-- privileges set in V3, but grant explicitly so fresh and upgraded
-- databases behave identically.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.widget_content TO monotron_app;
