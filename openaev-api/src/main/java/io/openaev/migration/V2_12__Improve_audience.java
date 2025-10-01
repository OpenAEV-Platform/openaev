package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_12__Improve_audience extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // created_at / updated_at
    select.execute(
        "ALTER TABLE audiences ADD audience_created_at timestamp not null default now();");
    select.execute(
        "ALTER TABLE audiences ADD audience_updated_at timestamp not null default now();");
  }
}
