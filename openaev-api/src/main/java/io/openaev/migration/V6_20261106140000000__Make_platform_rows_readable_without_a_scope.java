package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Redefines {@code can_access_tenant(row_tenant_id, allow_platform)} so that a platform row ({@code
 * row_tenant_id IS NULL}) read with {@code allow_platform} is visible whatever {@code
 * app.current_tenants} holds, an unset or empty scope included. A platform row belongs to no tenant
 * and is already readable from every tenant and from the anonymous public settings endpoint, so
 * reading it with no scope exposes nothing. Every other answer is unchanged: a tenant row needs its
 * tenant in the scope, and a null row_tenant_id of a strict table (no {@code allow_platform}) is
 * still refused.
 *
 * <p>Adds {@code require_tenant_write(row_tenant_id, allow_platform_write)}, the raising form of
 * {@code can_write_tenant}: it returns true when the write is allowed and raises when it is not, so
 * an {@code INSERT} the policy refuses ends in an error the caller sees instead of inserting no
 * row. Its cost is declared high so the planner evaluates it after the statement's own conditions,
 * and it only ever raises for a row the statement would otherwise have written.
 */
@Component
public class V6_20261106140000000__Make_platform_rows_readable_without_a_scope
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_access_tenant(
              row_tenant_id  text,
              allow_platform boolean DEFAULT false)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT CASE
              WHEN allow_platform AND row_tenant_id IS NULL THEN true
              WHEN current_setting('app.current_tenants', true) IS NULL
                OR current_setting('app.current_tenants', true) = '' THEN false
              ELSE COALESCE(
                     row_tenant_id = ANY (
                       string_to_array(current_setting('app.current_tenants', true), ',')),
                     false)
            END
          $$;
          """);
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION require_tenant_write(
              row_tenant_id        text,
              allow_platform_write boolean DEFAULT false)
          RETURNS boolean
          LANGUAGE plpgsql STABLE PARALLEL SAFE COST 10000 AS $$
          BEGIN
            IF can_write_tenant(row_tenant_id, allow_platform_write) THEN
              RETURN true;
            END IF;
            IF row_tenant_id IS NULL THEN
              RAISE EXCEPTION 'write of a platform row refused: the transaction is not platform-write'
                USING ERRCODE = '42501';
            END IF;
            RAISE EXCEPTION 'write of a row of tenant % refused: outside the scope of the transaction',
              row_tenant_id USING ERRCODE = '42501';
          END
          $$;
          """);
    }
  }
}
