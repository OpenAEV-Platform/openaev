package io.openaev.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Installs the write-attribution detection trigger. A BEFORE INSERT OR UPDATE trigger on every base
 * table carrying a {@code tenant_id} column (except the self-isolated tables) raises a {@code
 * [WRITEATTR]} warning when a v2 scope is set on the transaction ({@code app.current_tenants}
 * non-empty) and the row's {@code tenant_id} is either outside that scope or null.
 *
 * <p>The out-of-scope condition mirrors {@code can_access_tenant} (migration V5_27) exactly, so the
 * detector and the read filter agree on what "inside the scope" means. The null case is raised for
 * every table and classified in Java ({@link WriteAttrDetectorListener}): a null tenant is a
 * misattribution on a strict table (which can never hold a platform row) and by-design on a
 * dual-scope table. Strict versus dual is an entity-model property the SQL layer does not have, so
 * the trigger raises the raw fact and Java decides.
 *
 * <ul>
 *   <li>scope empty (no v2 scope set) -> silent. A background path without a scope is a separate
 *       concern; this detector is the write-attribution twin of the read filter.
 *   <li>{@code NEW.tenant_id} in the scope list -> silent. A correctly attributed write, including
 *       the {@code fallbackSelector} default-tenant write, whose scope IS the default tenant.
 *   <li>{@code NEW.tenant_id} NULL -> raised as {@code tenant=NULL}; Java flags it only on a strict
 *       table.
 *   <li>{@code allTenants()} resolves to the explicit list of every active tenant, so any active
 *       tenant is in scope and the detector is silent, which is the intended behaviour.
 * </ul>
 *
 * <p>The install loops over {@code information_schema}, so a newly activated table's trigger
 * appears with no code change (schema-drift safe), except a table created after the install runs,
 * which needs a re-install to be covered (see the near-miss coverage test). It is a DB trigger
 * rather than SQL parsing on purpose: it reads {@code NEW.tenant_id} directly, so it is immune to
 * statement shape (JPA, native, {@code JdbcTemplate}, {@code ON CONFLICT} upserts, {@code INSERT
 * ... SELECT}, batch).
 *
 * <p>The install is versioned and self-cleaning. {@link #install} drops any prior version first, so
 * a run that crashed without {@link #uninstall} cannot leave a differently shaped trigger that
 * changes the next run; a stale version found on install is reinstalled with a printed notice
 * rather than reused silently. {@link #uninstall} removes the trigger from every table and drops
 * the function, run when the detector context closes.
 */
final class WriteAttrDetectorTrigger {

  static final String MARKER = "[WRITEATTR]";

  /**
   * Bumped whenever the function body changes. Stored as the function comment so a leftover trigger
   * of an older shape is detected on install rather than silently reused.
   */
  static final String VERSION = "3";

  private static final String FUNCTION = "_writeattr_detect";
  private static final String TRIGGER = "_writeattr_trg";

  /**
   * The warning now carries the written row's primary key ({@code id=%}), so the Java side can
   * match the write to the entry frame captured for that entity at {@code persist}/{@code merge}
   * time. The primary-key column is resolved once per table at attach time and passed as the
   * trigger argument ({@code TG_ARGV[0]}), so the per-row path is a single field read, not a
   * catalog lookup. A table with a composite or absent primary key gets an empty argument; its
   * writes are reported with {@code id=?} and fall back to stack-based attribution on the Java
   * side.
   */
  private static final String CREATE_FUNCTION =
      """
      CREATE OR REPLACE FUNCTION _writeattr_detect() RETURNS trigger
      LANGUAGE plpgsql AS $$
      DECLARE
        scope text := current_setting('app.current_tenants', true);
        pkcol text := CASE WHEN TG_NARGS >= 1 THEN TG_ARGV[0] ELSE '' END;
        rowid text := '?';
      BEGIN
        IF scope IS NOT NULL AND scope <> '' THEN
          IF NEW.tenant_id IS NULL OR NOT (NEW.tenant_id = ANY(string_to_array(scope, ','))) THEN
            IF pkcol <> '' THEN
              EXECUTE format('SELECT ($1).%I::text', pkcol) INTO rowid USING NEW;
            END IF;
            IF NEW.tenant_id IS NULL THEN
              RAISE WARNING '[WRITEATTR] table=% id=% tenant=NULL scope=%',
                TG_TABLE_NAME, COALESCE(rowid, '?'), scope;
            ELSE
              RAISE WARNING '[WRITEATTR] table=% id=% tenant=% scope=%',
                TG_TABLE_NAME, COALESCE(rowid, '?'), NEW.tenant_id, scope;
            END IF;
          END IF;
        END IF;
        RETURN NEW;
      END;
      $$;
      """;

  private static final String COMMENT_VERSION =
      "COMMENT ON FUNCTION _writeattr_detect() IS 'writeattr-v" + VERSION + "'";

  // to_regprocedure returns NULL when the function is absent, instead of raising (which would abort
  // the surrounding transaction); obj_description(NULL, ...) is NULL. So this never errors.
  private static final String INSTALLED_VERSION_QUERY =
      "SELECT obj_description(to_regprocedure('_writeattr_detect()'), 'pg_proc')";

  private WriteAttrDetectorTrigger() {}

  /**
   * Creates the function and attaches the trigger to every tenant table except {@code
   * excludedTables}. Idempotent: it drops any prior trigger and function first, so a re-run and a
   * newly onboarded table both converge without error and a stale version cannot survive.
   */
  static void install(Connection connection, Set<String> excludedTables) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      String stale = installedVersion(statement);
      if (stale != null && !("writeattr-v" + VERSION).equals(stale)) {
        // Not silent: a leftover trigger of another shape is being replaced, not trusted.
        System.out.println(
            MARKER + " reinstalling a stale trigger (" + stale + " -> writeattr-v" + VERSION + ")");
      }
      uninstall(statement);
      statement.execute(CREATE_FUNCTION);
      statement.execute(COMMENT_VERSION);
      statement.execute(attachTriggers(excludedTables));
    }
  }

  /** Removes the trigger from every tenant table and drops the function. Idempotent. */
  static void uninstall(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      uninstall(statement);
    }
  }

  private static void uninstall(Statement statement) throws SQLException {
    statement.execute(
        """
        DO $$
        DECLARE r record;
        BEGIN
          FOR r IN
            SELECT event_object_table AS table_name
            FROM information_schema.triggers
            WHERE trigger_name = '_writeattr_trg' AND trigger_schema = 'public'
          LOOP
            EXECUTE format('DROP TRIGGER IF EXISTS _writeattr_trg ON %I;', r.table_name);
          END LOOP;
        END $$;
        """);
    statement.execute("DROP FUNCTION IF EXISTS _writeattr_detect()");
  }

  private static String installedVersion(Statement statement) throws SQLException {
    try (ResultSet rs = statement.executeQuery(INSTALLED_VERSION_QUERY)) {
      return rs.next() ? rs.getString(1) : null;
    } catch (SQLException e) {
      // The function does not exist yet: nothing installed, so nothing stale.
      return null;
    }
  }

  private static String attachTriggers(Set<String> excludedTables) {
    String excluded =
        excludedTables.stream()
            .map(name -> "'" + name.toLowerCase().replace("'", "''") + "'")
            .collect(Collectors.joining(","));
    String notExcluded = excluded.isEmpty() ? "" : " AND c.table_name NOT IN (" + excluded + ")";
    // Plain concatenation, not String.formatted: the body carries PostgreSQL format() specifiers
    // (%I, %L) that a Java formatter would fight over. Each table's single-column primary key is
    // resolved here and passed to the trigger as %L; a composite or absent PK resolves to '' and
    // the
    // trigger reports id=?.
    return "DO $$\n"
        + "DECLARE r record; pk text;\n"
        + "BEGIN\n"
        + "  FOR r IN\n"
        + "    SELECT c.table_name\n"
        + "    FROM information_schema.columns c\n"
        + "    JOIN information_schema.tables t\n"
        + "      ON t.table_schema = c.table_schema AND t.table_name = c.table_name\n"
        + "    WHERE c.column_name = 'tenant_id'\n"
        + "      AND c.table_schema = 'public'\n"
        + "      AND t.table_type = 'BASE TABLE'"
        + notExcluded
        + "\n"
        + "  LOOP\n"
        + "    SELECT CASE WHEN count(*) = 1 THEN max(a.attname) ELSE '' END\n"
        + "      INTO pk\n"
        + "    FROM pg_index i\n"
        + "    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)\n"
        + "    WHERE i.indrelid = format('public.%I', r.table_name)::regclass\n"
        + "      AND i.indisprimary;\n"
        + "    EXECUTE format(\n"
        + "      'DROP TRIGGER IF EXISTS "
        + TRIGGER
        + " ON %I;'\n"
        + "      ' CREATE TRIGGER "
        + TRIGGER
        + " BEFORE INSERT OR UPDATE ON %I'\n"
        + "      ' FOR EACH ROW EXECUTE FUNCTION "
        + FUNCTION
        + "(%L);',\n"
        + "      r.table_name, r.table_name, COALESCE(pk, ''));\n"
        + "  END LOOP;\n"
        + "END $$;\n";
  }
}
