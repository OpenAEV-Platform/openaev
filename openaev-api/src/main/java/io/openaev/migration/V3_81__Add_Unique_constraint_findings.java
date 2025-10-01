package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_81__Add_Unique_constraint_findings extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();

    select.execute(
        " DELETE FROM findings WHERE ctid NOT IN ("
            + "  SELECT min(ctid)"
            + "  FROM findings"
            + "  GROUP BY finding_inject_id, finding_value, finding_type, finding_field"
            + ");");

    select.execute(
        "ALTER TABLE findings ADD CONSTRAINT unique_finding_constraint UNIQUE (finding_inject_id, finding_value, finding_type, finding_field);");
  }
}
