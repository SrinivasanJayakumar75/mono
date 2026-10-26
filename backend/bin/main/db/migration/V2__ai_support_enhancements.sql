-- =====================================================================
-- RelayDesk — V2: AI support platform enhancements
--
-- Adds the columns / indexes required by the AI customer support feature
-- set: AI configuration per app, ingestion bookkeeping on documents,
-- chunk metadata, per-conversation AI bookkeeping and RAG citations on
-- messages.
-- =====================================================================

-- ---------------------------------------------------------------------
-- refresh_tokens: remember the tenant the session was issued for
-- ---------------------------------------------------------------------
ALTER TABLE public.refresh_tokens ADD COLUMN IF NOT EXISTS workspace_id character varying(36);
ALTER TABLE public.refresh_tokens ADD COLUMN IF NOT EXISTS user_agent character varying(500);

-- ---------------------------------------------------------------------
-- apps: per-tenant AI configuration
-- ---------------------------------------------------------------------
ALTER TABLE public.apps ADD COLUMN IF NOT EXISTS ai_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE public.apps ADD COLUMN IF NOT EXISTS auto_reply boolean NOT NULL DEFAULT true;
ALTER TABLE public.apps ADD COLUMN IF NOT EXISTS ai_model character varying(80);
ALTER TABLE public.apps ADD COLUMN IF NOT EXISTS ai_temperature numeric(3,2) NOT NULL DEFAULT 0.2;
ALTER TABLE public.apps ADD COLUMN IF NOT EXISTS ai_system_prompt text;

-- ---------------------------------------------------------------------
-- documents: ingestion bookkeeping
-- ---------------------------------------------------------------------
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS size_bytes bigint;
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS mime_type character varying(120);
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS content_hash character varying(64);
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS storage_key character varying(1000);

-- ---------------------------------------------------------------------
-- content_chunks: richer provenance for citations
-- ---------------------------------------------------------------------
ALTER TABLE public.content_chunks ADD COLUMN IF NOT EXISTS token_count integer;
ALTER TABLE public.content_chunks ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

-- ---------------------------------------------------------------------
-- articles
-- ---------------------------------------------------------------------
ALTER TABLE public.articles ADD COLUMN IF NOT EXISTS excerpt character varying(1000);

-- ---------------------------------------------------------------------
-- conversations: AI + unread bookkeeping
-- ---------------------------------------------------------------------
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS ai_handled boolean NOT NULL DEFAULT false;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS unread_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS channel character varying(16) NOT NULL DEFAULT 'WIDGET';

-- ---------------------------------------------------------------------
-- messages: RAG citations + feedback
-- ---------------------------------------------------------------------
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS citations jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS feedback character varying(16);

-- ---------------------------------------------------------------------
-- teams / users
-- ---------------------------------------------------------------------
ALTER TABLE public.teams ADD COLUMN IF NOT EXISTS ai_enabled boolean NOT NULL DEFAULT true;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS last_login_at timestamp with time zone;

-- ---------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_documents_workspace_status ON public.documents USING btree (workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_conversations_workspace_activity ON public.conversations USING btree (workspace_id, last_activity_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_chunks_source_order ON public.content_chunks USING btree (source_type, source_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON public.messages USING btree (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_workspace_status ON public.conversations USING btree (workspace_id, status, last_activity_at DESC);
