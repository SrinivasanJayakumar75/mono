-- =====================================================================
-- RelayDesk - V5: remove billing feature schema
-- =====================================================================

DROP TABLE IF EXISTS public.billing_payments CASCADE;
DROP TABLE IF EXISTS public.billing_orders CASCADE;
DROP TABLE IF EXISTS public.billing_subscriptions CASCADE;