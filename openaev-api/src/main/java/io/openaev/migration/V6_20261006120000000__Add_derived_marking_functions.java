package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the read-time visibility functions of the derived marking dimension (Task 4, Option 2 "hide
 * parents", variant C-2): a row of a derived table is visible only when none of the assets it holds
 * is outside the caller's clearance.
 *
 * <p>No column is added anywhere: each function follows the row's links down to {@code
 * assets.marking_ids} and reuses {@code is_marking_set_allowed}, so the answer is always computed
 * from the current markings and needs no maintenance on write.
 *
 * <p><b>These must stay database functions, never SQL inlined into the rewritten statement.</b> The
 * statement inspector rewrites the SQL Hibernate emits, not the body of a function, so {@code
 * assets} read <i>inside</i> a function is not narrowed by the caller's clearance. That is the
 * point: the check must <i>find</i> the restricted asset to hide its parent. Inlined, the same
 * {@code assets} reference could be filtered too, and the check would silently answer "nothing
 * restricted".
 *
 * <p>Tenant isolation is preserved without a tenant predicate here: the functions only follow
 * foreign keys from a row that has already passed the caller's tenant filter.
 *
 * <p>Asset-group membership is the <b>static</b> one only ({@code asset_groups_assets}). Dynamic
 * members are resolved in Java from {@code asset_group_dynamic_filter} and are not visible to SQL;
 * they are deliberately out of scope of this first iteration.
 */
@Component
public class V6_20261006120000000__Add_derived_marking_functions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Created in dependency order: a LANGUAGE sql body is validated on creation, so a function
      // must exist before another one calls it.
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_see_asset_group(p_asset_group_id varchar)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT NOT EXISTS (
              SELECT 1 FROM asset_groups_assets aga
              JOIN assets a ON a.asset_id = aga.asset_id
              WHERE aga.asset_group_id = p_asset_group_id
                AND NOT is_marking_set_allowed(a.marking_ids))
          $$;
          """);
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_see_inject(p_inject_id varchar)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT NOT EXISTS (
                     SELECT 1 FROM injects_assets ia
                     JOIN assets a ON a.asset_id = ia.asset_id
                     WHERE ia.inject_id = p_inject_id
                       AND NOT is_marking_set_allowed(a.marking_ids))
               AND NOT EXISTS (
                     SELECT 1 FROM injects_asset_groups iag
                     WHERE iag.inject_id = p_inject_id
                       AND NOT can_see_asset_group(iag.asset_group_id))
          $$;
          """);
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_see_finding(p_finding_id varchar, p_inject_id varchar)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT (p_inject_id IS NULL OR can_see_inject(p_inject_id))
               AND NOT EXISTS (
                     SELECT 1 FROM findings_assets fa
                     JOIN assets a ON a.asset_id = fa.asset_id
                     WHERE fa.finding_id = p_finding_id
                       AND NOT is_marking_set_allowed(a.marking_ids))
          $$;
          """);
    }
  }
}
