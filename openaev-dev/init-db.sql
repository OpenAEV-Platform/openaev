-- Run as superuser (openaev) during container initialization
-- Creates the app role and owner user for RLS support

-- 1. Create the app role that RLS will apply to
CREATE ROLE openaev_app NOLOGIN NOSUPERUSER;

-- 2. Create the migration/app user with enough privileges
CREATE USER openaev_owner WITH PASSWORD 'openaev' NOSUPERUSER CREATEROLE;

-- 3. Grant the app role to the owner (so SET ROLE works at runtime)
GRANT openaev_app TO openaev_owner;

-- 4. Make openaev_owner the owner of the database and public schema
ALTER DATABASE openaev OWNER TO openaev_owner;
ALTER SCHEMA public OWNER TO openaev_owner;

-- 5. Set default app.current_tenant so RLS policies don't fail on empty setting
ALTER DATABASE openaev SET app.current_tenant = '2cffad3a-0001-4078-b0e2-ef74274022c3';

