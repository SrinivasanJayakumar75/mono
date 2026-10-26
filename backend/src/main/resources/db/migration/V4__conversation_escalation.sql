-- =====================================================================
-- RelayDesk - V4: human escalation
--
-- When a conversation is escalated, the widget stops sending automatic
-- AI replies so a human agent can take over without the assistant
-- talking over them. Agents can toggle this from the inbox.
-- =====================================================================

ALTER TABLE public.conversations
    ADD COLUMN IF NOT EXISTS escalated boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_conversations_escalated
    ON public.conversations (app_id, escalated)
    WHERE escalated;
