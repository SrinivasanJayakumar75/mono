-- =====================================================================
-- RelayDesk - V9: Razorpay subscriptions and trial access
--
-- Billing is deliberately global (no Row Level Security) because Razorpay
-- webhooks arrive without a tenant. Every workspace-scoped query in
-- BillingService filters on workspace_id explicitly, and the public webhook
-- only touches rows addressed by a verified Razorpay subscription id.
-- =====================================================================

-- These are leftovers from the billing feature removed in V5. They were
-- recreated outside the migration history at some point and use an older
-- schema. Drop them so the subscription schema below is authoritative.
DROP TABLE IF EXISTS public.billing_payments CASCADE;
DROP TABLE IF EXISTS public.billing_orders CASCADE;
DROP TABLE IF EXISTS public.billing_plans CASCADE;
DROP TABLE IF EXISTS public.billing_subscriptions CASCADE;

CREATE TABLE IF NOT EXISTS public.billing_subscriptions (
    id character varying(36) PRIMARY KEY,
    workspace_id character varying(36) NOT NULL UNIQUE
        REFERENCES public.workspaces(id) ON DELETE CASCADE,
    provider character varying(32) NOT NULL DEFAULT 'razorpay',
    billing_interval character varying(16),
    status character varying(24) NOT NULL DEFAULT 'TRIALING',
    provider_plan_id character varying(64),
    provider_subscription_id character varying(64) UNIQUE,
    provider_customer_id character varying(64),
    current_start timestamp with time zone,
    current_end timestamp with time zone,
    trial_ends_at timestamp with time zone,
    cancel_at_period_end boolean NOT NULL DEFAULT false,
    short_url character varying(512),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT billing_subscriptions_interval_check
        CHECK (billing_interval IS NULL OR billing_interval IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT billing_subscriptions_status_check
        CHECK (status IN ('TRIALING', 'PENDING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_billing_subscriptions_workspace
    ON public.billing_subscriptions (workspace_id);

CREATE INDEX IF NOT EXISTS idx_billing_subscriptions_provider_sub
    ON public.billing_subscriptions (provider_subscription_id);

CREATE TABLE IF NOT EXISTS public.billing_plans (
    plan_key character varying(16) PRIMARY KEY,
    provider character varying(32) NOT NULL DEFAULT 'razorpay',
    provider_plan_id character varying(64) NOT NULL UNIQUE,
    amount integer NOT NULL,
    currency character varying(8) NOT NULL DEFAULT 'INR',
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT billing_plans_key_check CHECK (plan_key IN ('MONTHLY', 'YEARLY'))
);

CREATE TABLE IF NOT EXISTS public.billing_payments (
    id character varying(36) PRIMARY KEY,
    workspace_id character varying(36) NOT NULL
        REFERENCES public.workspaces(id) ON DELETE CASCADE,
    provider character varying(32) NOT NULL DEFAULT 'razorpay',
    provider_payment_id character varying(64) NOT NULL UNIQUE,
    provider_subscription_id character varying(64),
    amount integer NOT NULL,
    currency character varying(8) NOT NULL,
    status character varying(24) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_billing_payments_workspace
    ON public.billing_payments (workspace_id);

CREATE INDEX IF NOT EXISTS idx_billing_payments_subscription
    ON public.billing_payments (provider_subscription_id);

CREATE TABLE IF NOT EXISTS public.billing_webhook_events (
    event_id character varying(128) PRIMARY KEY,
    event_type character varying(80) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

-- Give every existing workspace a fresh three-day trial. New workspaces get
-- their trial row from AuthService at registration time.
INSERT INTO public.billing_subscriptions (
    id, workspace_id, status, trial_ends_at, current_start, current_end, created_at, updated_at
)
SELECT
    md5(random()::text || clock_timestamp()::text),
    w.id,
    'TRIALING',
    now() + interval '3 days',
    now(),
    now() + interval '3 days',
    now(),
    now()
FROM public.workspaces w
WHERE NOT EXISTS (
    SELECT 1 FROM public.billing_subscriptions bs WHERE bs.workspace_id = w.id
);

-- The runtime role is downgraded on every tenant request. Billing tables are
-- global, so the grant is explicit even though V3 sets default privileges.
GRANT SELECT, INSERT, UPDATE, DELETE
    ON public.billing_subscriptions, public.billing_plans,
       public.billing_payments, public.billing_webhook_events
    TO relaydesk_app;
