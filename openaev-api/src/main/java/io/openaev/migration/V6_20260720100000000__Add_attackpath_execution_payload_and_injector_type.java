package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the run's payload id and injector type on {@code attackpath_execution} (issue 5048). The
 * payload id lets the execution detail resolve the payload's detection remediations; the injector
 * type lets the graph label the injector node with its real type instead of the front guessing an
 * icon slug from the label. Both are captured at run time, additive and nullable (the seed and
 * injector-less rows carry none).
 */
@Component
public class V6_20260720100000000__Add_attackpath_execution_payload_and_injector_type
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_payload_id varchar(255), "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_injector_type varchar(255);");
    }
  }
}
