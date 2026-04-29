package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_99__Remove_xtmhub_settings_keys extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              DELETE FROM parameters
              WHERE parameter_key IN (
                  'xtm_hub_token',
                  'xtm_hub_registration_date',
                  'xtm_hub_registration_status',
                  'xtm_hub_registration_user_id',
                  'xtm_hub_registration_user_name',
                  'xtm_hub_last_connectivity_check'
              );
              """);
    }
  }
}
