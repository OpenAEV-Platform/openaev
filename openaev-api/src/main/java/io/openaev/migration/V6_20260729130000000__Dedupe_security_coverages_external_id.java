package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Deduplicates {@code security_coverages} on {@code security_coverage_external_id} and enforces it
 * with a unique constraint.
 *
 * <p>The table was created (V4_22) without a unique constraint on the external id, while {@code
 * SecurityCoverageService#getByExternalIdOrCreateSecurityCoverage} is a non-atomic find-then-create
 * upsert protected only by a JVM-local striped lock. Concurrent submissions of the same coverage
 * (OpenCTI worker retries, multiple API replicas, or the instability window of a full reindex)
 * could insert the same external id twice; once duplicated, every subsequent bundle for that
 * coverage failed with {@code IncorrectResultSizeDataAccessException} and the OpenCTI worker
 * retried forever (seen in production after the rolling upgrade).
 *
 * <p>Steps, all idempotent:
 *
 * <ol>
 *   <li>Delete duplicate coverage rows, keeping per external id the row linked to a scenario,
 *       breaking ties by latest update. Exercises pointing at a deleted row are detached by the
 *       existing {@code ON DELETE SET NULL} foreign key; scenarios generated for deleted rows are
 *       left in place (they are regular scenarios, deletable from the UI).
 *   <li>Add the unique constraint so the race can never create duplicates again: the losing insert
 *       now fails its own transaction and the OpenCTI retry finds the winner row.
 * </ol>
 */
@Component
public class V6_20260729130000000__Dedupe_security_coverages_external_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Map every duplicate coverage to the best row sharing its external id: prefer the row
      // already linked to a scenario, then the most recently updated one.
      statement.execute(
          """
          CREATE TEMPORARY TABLE tmp_sc_duplicates ON COMMIT DROP AS
          SELECT security_coverage_id AS duplicate_id,
                 FIRST_VALUE(security_coverage_id) OVER (
                   PARTITION BY security_coverage_external_id
                   ORDER BY (security_coverage_scenario IS NOT NULL) DESC,
                            security_coverage_updated_at DESC NULLS LAST,
                            security_coverage_id ASC
                 ) AS keeper_id
          FROM security_coverages;
          """);
      statement.execute(
          """
          DELETE FROM tmp_sc_duplicates WHERE duplicate_id = keeper_id;
          """);
      statement.execute(
          """
          DELETE FROM security_coverages sc
          USING tmp_sc_duplicates dup
          WHERE sc.security_coverage_id = dup.duplicate_id;
          """);
      // Enforce the natural key used by the upsert lookup. Guarded for idempotency:
      // ADD CONSTRAINT has no IF NOT EXISTS in PostgreSQL.
      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'security_coverages_external_id_unique'
            ) THEN
              ALTER TABLE security_coverages
                ADD CONSTRAINT security_coverages_external_id_unique
                UNIQUE (security_coverage_external_id);
            END IF;
          END $$;
          """);
    }
  }
}
