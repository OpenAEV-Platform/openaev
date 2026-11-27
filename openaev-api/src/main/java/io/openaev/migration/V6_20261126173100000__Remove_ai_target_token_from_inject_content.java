package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261126173100000__Remove_ai_target_token_from_inject_content
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE injects SET inject_content = (inject_content::jsonb - 'target_token')::text
          WHERE inject_content::jsonb ? 'target_token';
          """);
    }
  }
}
