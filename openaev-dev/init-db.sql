-- Run as superuser (openaev) during container initialization
-- Creates the app role needed for Row-Level Security (RLS)

-- 1. Create the non-superuser app role that RLS policies are enforced against.
--    At runtime, TenantAwareDataSourceConfig does SET ROLE openaev_app on every connection.
CREATE ROLE openaev_app NOLOGIN NOSUPERUSER;

-- 2. Grant the app role to the superuser so SET ROLE works at runtime
GRANT openaev_app TO openaev;

-- 3. Set default app.current_tenant so RLS policies don't fail on empty setting
ALTER DATABASE openaev SET app.current_tenant = '2cffad3a-0001-4078-b0e2-ef74274022c3';

