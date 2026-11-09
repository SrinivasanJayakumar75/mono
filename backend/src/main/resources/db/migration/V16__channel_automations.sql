-- =====================================================================
-- Monotron - V16: channel automation
--
-- Stores per-workspace automation preferences for the Gmail, WhatsApp and
-- Instagram channels. Conversations from these channels land in the shared
-- inbox; these settings decide whether the channel is active, whether the AI
-- replies automatically, and whether the conversation can be handed to a human.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.channel_automations (
    id character varying(36) PRIMARY KEY,
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying),
    channel character varying(16) NOT NULL,
    account_label character varying(200) NOT NULL DEFAULT '',
    enabled boolean NOT NULL DEFAULT false,
    auto_reply boolean NOT NULL DEFAULT true,
    handoff_to_human boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT channel_automations_channel_check
        CHECK (channel IN ('GMAIL', 'WHATSAPP', 'INSTAGRAM')),
    CONSTRAINT channel_automations_workspace_channel_key
        UNIQUE (workspace_id, channel)
);

ALTER TABLE public.channel_automations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_automations FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.channel_automations;
CREATE POLICY tenant_isolation ON public.channel_automations
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_channel_automations_workspace ON public.channel_automations;
CREATE TRIGGER trg_channel_automations_workspace BEFORE INSERT ON public.channel_automations
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_channel_automations_workspace
    ON public.channel_automations (workspace_id);

COMMENT ON TABLE public.channel_automations IS
    'Per-workspace automation settings for external chat channels.';

GRANT SELECT, INSERT, UPDATE, DELETE ON public.channel_automations TO monotron_app;
