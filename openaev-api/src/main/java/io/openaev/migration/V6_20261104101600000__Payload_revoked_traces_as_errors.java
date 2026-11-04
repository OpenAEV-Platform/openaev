package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** A refused revoked payload is a plain error trace; its message still says it was revoked. */
@Component
public class V6_20261104101600000__Payload_revoked_traces_as_errors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "UPDATE execution_traces SET execution_status = 'ERROR'"
              + " WHERE execution_status = 'PAYLOAD_REVOKED';");
    }
  }
}
