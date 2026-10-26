-- =====================================================================
-- RelayDesk — V3: the restricted runtime role
--
-- Row Level Security only applies to roles that are neither superusers nor
-- granted BYPASSRLS. The application therefore connects as an administrative
-- account and downgrades every tenant-bound transaction to this role:
--
--     SET LOCAL ROLE relaydesk_app;
--
-- so that the policies are evaluated for real. This migration makes the role
-- part of the schema so a freshly created database is secured as well.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'relaydesk_app') THEN
        CREATE ROLE relaydesk_app NOLOGIN;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO relaydesk_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO relaydesk_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO relaydesk_app;

-- Future tables created by the migration owner inherit the same grants.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO relaydesk_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO relaydesk_app;
