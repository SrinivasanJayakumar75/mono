-- =====================================================================
-- Monotron - V19: Meta channel messaging
--
-- WhatsApp and Instagram webhooks arrive without a bearer token. The
-- channel_account_lookup table is the deliberately non-RLS bridge from a
-- provider account id (WhatsApp phone number id or Instagram account id) to
-- the workspace that connected it. It contains no credentials.
--
-- The metadata column on channel_credentials stores non-secret provider ids
-- (for example the WhatsApp phone number id / WABA id, or the Instagram page
-- id / Instagram account id) needed to send replies.
-- =====================================================================

ALTER TABLE public.channel_credentials
    ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS public.channel_account_lookup (
    channel character varying(16) NOT NULL,
    external_account_id character varying(200) NOT NULL,
    workspace_id character varying(36) NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT channel_account_lookup_channel_check
        CHECK (channel IN ('WHATSAPP', 'INSTAGRAM')),
    CONSTRAINT channel_account_lookup_provider_key
        PRIMARY KEY (channel, external_account_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.channel_account_lookup TO monotron_app;

COMMENT ON TABLE public.channel_account_lookup IS
    'Non-secret provider account to workspace lookup used by public Meta webhooks.';

COMMENT ON COLUMN public.channel_credentials.metadata IS
    'Non-secret provider ids used for sending replies (WhatsApp phone number / WABA, Instagram page / IG account).';
