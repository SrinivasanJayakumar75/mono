-- =====================================================================
-- Monotron - V14: Polar subscription defaults
--
-- The gateway moved from Razorpay to Polar. Historical rows are left alone
-- so past payments keep their original provider label; new rows default to
-- Polar. Existing trial rows (which have no provider subscription) are
-- relabelled because they were never tied to a Razorpay subscription.
-- =====================================================================

ALTER TABLE public.billing_subscriptions
    ALTER COLUMN provider SET DEFAULT 'polar';

ALTER TABLE public.billing_payments
    ALTER COLUMN provider SET DEFAULT 'polar';

ALTER TABLE public.billing_plans
    ALTER COLUMN provider SET DEFAULT 'polar';

UPDATE public.billing_subscriptions
SET provider = 'polar', updated_at = now()
WHERE provider = 'razorpay'
  AND provider_subscription_id IS NULL;
