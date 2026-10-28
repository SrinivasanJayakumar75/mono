-- =====================================================================
-- Monotron - V13: auto-open messenger widget
--
-- When enabled, the embedded messenger opens its chat panel as soon as
-- the widget loads on the customer's site. The setting is exposed in the
-- public widget config so the iframe can decide whether to start open.
-- =====================================================================

ALTER TABLE public.widget_settings
    ADD COLUMN IF NOT EXISTS auto_open boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN public.widget_settings.auto_open IS
    'Open the messenger panel automatically when the widget loads.';
