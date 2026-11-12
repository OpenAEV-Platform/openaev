package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives {@code execution_traces} its own {@code tenant_id}, so that each trace is scoped on its own
 * column, with no join through its status's inject.
 *
 * <p>Backfill, in this order:
 *
 * <ul>
 *   <li>a trace of an inject status takes the tenant of that status's inject;
 *   <li>a trace of an inject test status takes the tenant of that status's inject;
 *   <li>a trace whose parent gives no tenant (an inject status with a null {@code status_inject})
 *       goes to the default tenant, and is counted in the log.
 * </ul>
 *
 * <p>The column is left with no default: a row written without a tenant fails on {@code NOT NULL},
 * and none lands silently in the default tenant.
 *
 * <p>The upgrade runs with the application stopped. The passes over the table (three updates,
 * {@code SET NOT NULL}, the foreign key validation and the index build) share one transaction and
 * its lock, as the rewrite of {@code V3_93} and the indexes of {@code V5_18} do.
 */
@Slf4j
@Component
public class V6_20261112060000000__Add_tenant_id_to_execution_traces extends BaseJavaMigration {

  public static final String TABLE = "execution_traces";

  @Override
  public void migrate(Context context) throws Exception {
    // Table and column names below are constants of this class, never input.
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)");

      statement.executeUpdate(
          "UPDATE "
              + TABLE
              + " t SET tenant_id = i.tenant_id FROM injects_statuses s"
              + " JOIN injects i ON i.inject_id = s.status_inject"
              + " WHERE t.tenant_id IS NULL AND t.execution_inject_status_id = s.status_id");
      statement.executeUpdate(
          "UPDATE "
              + TABLE
              + " t SET tenant_id = i.tenant_id FROM injects_tests_statuses s"
              + " JOIN injects i ON i.inject_id = s.status_inject"
              + " WHERE t.tenant_id IS NULL AND t.execution_inject_test_status_id = s.status_id");

      int orphans =
          statement.executeUpdate(
              "UPDATE "
                  + TABLE
                  + " SET tenant_id = '"
                  + DEFAULT_TENANT_UUID
                  + "' WHERE tenant_id IS NULL");
      if (orphans > 0) {
        log.warn(
            "{} row(s) of {} have no parent to take a tenant from and were attributed to the"
                + " default tenant",
            orphans,
            TABLE);
      }

      statement.execute("ALTER TABLE " + TABLE + " ALTER COLUMN tenant_id SET NOT NULL");
      statement.execute(
          "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_"
              + TABLE
              + "_tenant_id') THEN ALTER TABLE "
              + TABLE
              + " ADD CONSTRAINT fk_"
              + TABLE
              + "_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)"
              + " ON DELETE CASCADE; END IF; END $$");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_tenant_id ON " + TABLE + "(tenant_id)");
    }
  }

  /* ROLLBACK
   ALTER TABLE execution_traces DROP COLUMN IF EXISTS tenant_id;
   -- Dropping the column drops its foreign key and index. Only valid together with a revert of the
   -- entity mapping, which writes the column on every insert.
  */
}
