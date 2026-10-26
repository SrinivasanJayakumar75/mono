-- =====================================================================
-- RelayDesk - V10: link CRM leads back to their inbox contact
--
-- The inbox can promote a widget visitor into a CRM lead with one click.
-- Keeping the contact id on the lead makes that action idempotent and lets
-- the conversation show an "In CRM" state after a reload.
-- =====================================================================

ALTER TABLE public.leads
    ADD COLUMN IF NOT EXISTS source_contact_id character varying(36);

CREATE UNIQUE INDEX IF NOT EXISTS uq_leads_workspace_source_contact
    ON public.leads (workspace_id, source_contact_id)
    WHERE source_contact_id IS NOT NULL;
