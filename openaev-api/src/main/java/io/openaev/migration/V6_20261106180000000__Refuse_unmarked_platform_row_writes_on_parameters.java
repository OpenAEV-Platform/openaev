package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a row trigger on {@code parameters} that applies the platform-write rule to every client of
 * the connection: an {@code INSERT}, {@code UPDATE} or {@code DELETE} of a platform row ({@code
 * tenant_id IS NULL}, before or after the statement) goes through {@code require_tenant_write(NULL,
 * true)}, which raises unless the transaction set {@code app.platform_write}. The statement
 * inspector applies the same rule to what Hibernate sends; the trigger applies it to a {@code
 * JdbcTemplate} or a JDBC statement in the same transaction, whose SQL the inspector never sees.
 *
 * <p>Only platform rows are checked: a tenant row is written by tenant creation, tenant deletion
 * (the {@code ON DELETE CASCADE} of {@code tenants}) and the test fixtures with no scope, none of
 * which sets {@code app.current_tenants}. A Flyway migration that writes a platform row of {@code
 * parameters} after this one runs {@code SELECT set_config('app.platform_write', 'true', true)}
 * first, in the same transaction.
 */
@Component
public class V6_20261106180000000__Refuse_unmarked_platform_row_writes_on_parameters
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION parameters_guard_platform_row()
          RETURNS trigger
          LANGUAGE plpgsql AS $$
          BEGIN
            IF (TG_OP <> 'INSERT' AND OLD.tenant_id IS NULL)
               OR (TG_OP <> 'DELETE' AND NEW.tenant_id IS NULL) THEN
              PERFORM require_tenant_write(NULL, true);
            END IF;
            IF TG_OP = 'DELETE' THEN
              RETURN OLD;
            END IF;
            RETURN NEW;
          END
          $$;
          """);
      statement.execute("DROP TRIGGER IF EXISTS parameters_guard_platform_row ON parameters;");
      statement.execute(
          """
          CREATE TRIGGER parameters_guard_platform_row
          BEFORE INSERT OR UPDATE OR DELETE ON parameters
          FOR EACH ROW EXECUTE FUNCTION parameters_guard_platform_row();
          """);
    }
  }
}
