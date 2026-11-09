-- =====================================================================
-- Monotron - V18: inbound Gmail message bookkeeping
-- =====================================================================

ALTER TABLE public.conversations
    ADD COLUMN IF NOT EXISTS external_thread_id character varying(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_conversations_channel_thread
    ON public.conversations (app_id, channel, external_thread_id)
    WHERE external_thread_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.channel_inbound_messages (
    id character varying(36) PRIMARY KEY,
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying),
    channel character varying(16) NOT NULL,
    provider_message_id character varying(255) NOT NULL,
    conversation_id character varying(36) NOT NULL,
    received_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT channel_inbound_messages_channel_check
        CHECK (channel IN ('GMAIL', 'WHATSAPP', 'INSTAGRAM')),
    CONSTRAINT channel_inbound_messages_conversation_fk
        FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE,
    CONSTRAINT channel_inbound_messages_provider_key
        UNIQUE (workspace_id, channel, provider_message_id)
);

ALTER TABLE public.channel_inbound_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_inbound_messages FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.channel_inbound_messages;
CREATE POLICY tenant_isolation ON public.channel_inbound_messages
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_channel_inbound_messages_workspace ON public.channel_inbound_messages;
CREATE TRIGGER trg_channel_inbound_messages_workspace
    BEFORE INSERT ON public.channel_inbound_messages
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_channel_inbound_messages_workspace
    ON public.channel_inbound_messages (workspace_id, channel, received_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.channel_inbound_messages TO monotron_app;

COMMENT ON TABLE public.channel_inbound_messages IS
    'Provider message ids already imported into the shared inbox, scoped per workspace.';
