-- =====================================================================
-- Monotron - V17: encrypted channel credentials
--
-- OAuth tokens for Gmail, WhatsApp and Instagram are stored per workspace.
-- Tokens are encrypted by the application before they reach PostgreSQL; the
-- database only sees ciphertext. RLS keeps one workspace's tokens invisible to
-- every other workspace.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.channel_credentials (
    id character varying(36) PRIMARY KEY,
    workspace_id character varying(36) NOT NULL
        DEFAULT COALESCE(public.app_workspace_id(), 'default-workspace'::character varying),
    channel character varying(16) NOT NULL,
    external_account_id character varying(200),
    account_label character varying(200) NOT NULL DEFAULT '',
    encrypted_access_token text,
    encrypted_refresh_token text,
    token_expires_at timestamp with time zone,
    scopes character varying(1000) NOT NULL DEFAULT '',
    connected_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT channel_credentials_channel_check
        CHECK (channel IN ('GMAIL', 'WHATSAPP', 'INSTAGRAM')),
    CONSTRAINT channel_credentials_workspace_channel_key
        UNIQUE (workspace_id, channel)
);

ALTER TABLE public.channel_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_credentials FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON public.channel_credentials;
CREATE POLICY tenant_isolation ON public.channel_credentials
    USING (((workspace_id)::text = (public.app_workspace_id())::text))
    WITH CHECK (((workspace_id)::text = (public.app_workspace_id())::text));

DROP TRIGGER IF EXISTS trg_channel_credentials_workspace ON public.channel_credentials;
CREATE TRIGGER trg_channel_credentials_workspace BEFORE INSERT ON public.channel_credentials
    FOR EACH ROW EXECUTE FUNCTION public.force_row_workspace();

CREATE INDEX IF NOT EXISTS idx_channel_credentials_workspace
    ON public.channel_credentials (workspace_id);

COMMENT ON TABLE public.channel_credentials IS
    'Encrypted OAuth credentials for external chat channels, scoped per workspace.';

GRANT SELECT, INSERT, UPDATE, DELETE ON public.channel_credentials TO monotron_app;
