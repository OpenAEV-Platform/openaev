package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code is_marking_set_allowed(row_marking_ids)}: true when the caller holds <b>every</b>
 * marking carried by the row, according to the clearance in the {@code app.current_markings}
 * setting.
 *
 * <p>A marking is a many-to-many association, but the set is stored inline on the marked row as a
 * {@code text[]}, so visibility is a containment test rather than a join:
 *
 * <pre>{@code
 * is_marking_set_allowed(t.marking_ids)   -- one local column, like can_access_tenant
 * }</pre>
 *
 * <p>{@code <@} ("is contained by") <b>is</b> the AND semantics of STIX {@code
 * object_marking_refs}: every marking on the row must be in the clearance. Two consequences follow
 * for free, and both are the intended behaviour — an unmarked row is the empty set, and the empty
 * set is contained in everything, so it stays visible to everyone; and adding a marking can only
 * ever reduce visibility.
 *
 * <p>The two {@code COALESCE}s are load-bearing rather than defensive. {@code NULL <@ anything} and
 * {@code anything <@ NULL} both yield NULL, which a WHERE clause drops — so without them a row with
 * a NULL {@code marking_ids} would be hidden (wrong: it is unmarked) and, with no clearance set,
 * every row including the unmarked ones would disappear. Normalising both sides to {@code '{}'}
 * gives the right answer on both counts: no clearance hides every marked row and keeps the unmarked
 * ones.
 *
 * <p><b>Do not rewrite this as {@code NOT (row_marking_ids && lacked_markings)}.</b> Overlap
 * against the set of markings the caller lacks is the GIN-friendly formulation and is therefore
 * tempting, but that set is "every marking minus mine": a marking definition created after the
 * clearance was resolved is in neither, so rows carrying it become <b>visible</b>. Containment
 * against the held set fails closed on the same event. The fast form and the correct form are not
 * the same form.
 *
 * <p>Ordinality (TLP:RED covers TLP:AMBER covers TLP:GREEN…) is resolved in Java when the clearance
 * is built, so the setting holds the <b>expanded</b> set of marking ids and this function stays a
 * plain containment test with no knowledge of marking types or orders.
 */
@Component
public class V6_20260825090000000__Add_is_marking_set_allowed_function extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION is_marking_set_allowed(row_marking_ids text[])
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT COALESCE(row_marking_ids, '{}'::text[])
                   <@ COALESCE(
                        string_to_array(
                          NULLIF(current_setting('app.current_markings', true), ''), ','),
                        '{}'::text[])
          $$;
          """);
    }
  }
}
