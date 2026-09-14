package io.openaev.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Installs the write-attribution detection trigger. A BEFORE INSERT OR UPDATE trigger on every base
 * table carrying a {@code tenant_id} column raises a {@code [WRITEATTR]} warning when a v2 scope is
 * set on the transaction ({@code app.current_tenants} non-empty) and the row's {@code tenant_id} is
 * outside that scope.
 *
 * <p>The condition mirrors {@code can_access_tenant} (migration V5_27) exactly, so the detector and
 * the read filter agree on what "inside the scope" means:
 *
 * <ul>
 *   <li>scope empty (no v2 scope set) -> silent. A background path without a scope is a separate
 *       concern; this detector is the write-attribution twin of the read filter.
 *   <li>{@code NEW.tenant_id} in the scope list -> silent. This is a correctly attributed write,
 *       including the {@code fallbackSelector} default-tenant write, whose scope IS the default
 *       tenant.
 *   <li>{@code NEW.tenant_id} NULL -> silent here (a platform/global row). Whether a null-tenant
 *       write under a restricted scope is by-design is an open question.
 *   <li>{@code allTenants()} resolves to the explicit list of every active tenant, so any active
 *       tenant is in scope and the detector is silent, which is the intended behaviour.
 * </ul>
 *
 * <p>The install loops over {@code information_schema}, so a newly activated table's trigger
 * appears with no code change (schema-drift safe). It is a DB trigger rather than SQL parsing on
 * purpose: it reads {@code NEW.tenant_id} directly, so it is immune to statement shape (JPA,
 * native, {@code JdbcTemplate}, {@code ON CONFLICT} upserts, {@code INSERT ... SELECT}, batch).
 */
final class WriteAttrDetectorTrigger {

  static final String MARKER = "[WRITEATTR]";

  private static final String CREATE_FUNCTION =
      """
      CREATE OR REPLACE FUNCTION _writeattr_detect() RETURNS trigger
      LANGUAGE plpgsql AS $$
      DECLARE scope text := current_setting('app.current_tenants', true);
      BEGIN
        IF scope IS NOT NULL AND scope <> '' AND NEW.tenant_id IS NOT NULL
           AND NOT (NEW.tenant_id = ANY(string_to_array(scope, ','))) THEN
          RAISE WARNING '[WRITEATTR] table=% tenant=% scope=%',
            TG_TABLE_NAME, NEW.tenant_id, scope;
        END IF;
        RETURN NEW;
      END;
      $$;
      """;

  // Attach the trigger to every base table that carries a tenant_id column. Idempotent: DROP then
  // CREATE, so a re-run and a newly onboarded table both converge without error.
  private static final String ATTACH_TRIGGERS =
      """
      DO $$
      DECLARE r record;
      BEGIN
        FOR r IN
          SELECT c.table_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_schema = c.table_schema AND t.table_name = c.table_name
          WHERE c.column_name = 'tenant_id'
            AND c.table_schema = 'public'
            AND t.table_type = 'BASE TABLE'
        LOOP
          EXECUTE format(
            'DROP TRIGGER IF EXISTS _writeattr_trg ON %I;'
            ' CREATE TRIGGER _writeattr_trg BEFORE INSERT OR UPDATE ON %I'
            ' FOR EACH ROW EXECUTE FUNCTION _writeattr_detect();',
            r.table_name, r.table_name);
        END LOOP;
      END $$;
      """;

  private WriteAttrDetectorTrigger() {}

  static void install(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(CREATE_FUNCTION);
      statement.execute(ATTACH_TRIGGERS);
    }
  }
}
