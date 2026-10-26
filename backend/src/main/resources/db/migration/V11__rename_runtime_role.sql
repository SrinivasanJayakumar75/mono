-- Monotron - V11: rename the restricted runtime role
--
-- The role was introduced as relaydesk_app in V3. Fresh databases replay that
-- migration, so this step renames it to the Monotron name. Existing databases
-- may already have been renamed out-of-band, in which case this is a no-op.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'relaydesk_app')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monotron_app') THEN
        ALTER ROLE relaydesk_app RENAME TO monotron_app;
    END IF;
END $$;
