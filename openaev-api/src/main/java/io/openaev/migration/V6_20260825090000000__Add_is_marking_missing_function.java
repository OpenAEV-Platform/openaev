package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code is_marking_missing(row_marking_id)}: true when the caller does <b>not</b> hold that
 * marking, according to the clearance in the {@code app.current_markings} setting.
 *
 * <p>The negative form is deliberate. A row carries <b>many</b> markings through a join table, so
 * visibility is decided by an anti-join — "keep the row if it carries no marking I lack" — and this
 * function is the "I lack it" half of that sentence:
 *
 * <pre>{@code
 * NOT EXISTS (SELECT 1 FROM assets_markings am
 *              WHERE am.asset_id = t.asset_id
 *                AND is_marking_missing(am.marking_id))
 * }</pre>
 *
 * <p>A positively named function would read like a row-level visibility test and invite the
 * predicate {@code EXISTS (… AND can_access_marking(…))}, which is wrong twice over: it hides
 * unmarked rows and leaks rows carrying one held marking next to a missing one. Named this way, the
 * function cannot be mistaken for a visibility check.
 *
 * <p>Fail-closed therefore means returning <b>true</b>: no clearance set means every marking is
 * missing, so every marked row is hidden while unmarked rows stay visible to everyone. A null
 * marking id is treated as missing for the same reason.
 *
 * <p>Ordinality (TLP:RED covers TLP:AMBER covers TLP:GREEN…) is resolved in Java when the clearance
 * is built, so the setting holds the <b>expanded</b> set of marking ids and this function stays a
 * plain set-membership test with no knowledge of marking types or orders.
 */
@Component
public class V6_20260825090000000__Add_is_marking_missing_function extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION is_marking_missing(row_marking_id text)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT CASE
              WHEN current_setting('app.current_markings', true) IS NULL
                OR current_setting('app.current_markings', true) = '' THEN true
              ELSE COALESCE(
                     NOT (row_marking_id = ANY (
                       string_to_array(current_setting('app.current_markings', true), ','))),
                     true)
            END
          $$;
          """);
    }
  }
}
