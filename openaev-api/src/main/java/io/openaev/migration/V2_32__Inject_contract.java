package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_32__Inject_contract extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Injects
    select.execute("ALTER TABLE injects ADD inject_contract varchar(256);");
    // Update injects
    select.executeUpdate(
        "UPDATE injects SET inject_contract='138ad8f8-32f8-4a22-8114-aaa12322bd09' WHERE injects.inject_type = 'openex_email';");
    select.executeUpdate(
        "UPDATE injects SET inject_contract='d02e9132-b9d0-4daa-b3b1-4b9871f8472c' WHERE injects.inject_type = 'openex_manual';");
    select.executeUpdate(
        "UPDATE injects SET inject_contract='aeab9ed6-ae98-4b48-b8cc-2e91ac54f2f9' WHERE injects.inject_type = 'openex_mastodon';");
    select.executeUpdate(
        "UPDATE injects SET inject_contract='e9e902bc-b03d-4223-89e1-fca093ac79dd' WHERE injects.inject_type = 'openex_ovh_sms';");
  }
}
