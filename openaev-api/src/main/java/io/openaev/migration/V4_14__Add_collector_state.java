package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_14__Add_collector_state extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        """
                  ALTER TABLE collectors
                    ADD COLUMN collector_state JSONB DEFAULT '{}' ;
                  """);

    select.execute("ALTER TABLE cves RENAME COLUMN cve_cvss TO cve_cvss_v31;");
  }
}
