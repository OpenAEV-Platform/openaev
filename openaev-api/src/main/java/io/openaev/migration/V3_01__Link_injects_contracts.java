package io.openaev.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_01__Link_injects_contracts extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    // Migration datas
    ObjectMapper mapper = new ObjectMapper();
    ResultSet resultsInjects = statement.executeQuery("SELECT * FROM injects");

    PreparedStatement statementInjectors =
        connection.prepareStatement(
            "INSERT INTO injectors (injector_id, injector_name, injector_type) VALUES (?, ?, ?) ON CONFLICT DO NOTHING");
    PreparedStatement statementInjectorsContracts =
        connection.prepareStatement(
            "INSERT INTO injectors_contracts (injector_contract_id, injector_id, injector_contract_labels, injector_contract_content) VALUES (?, ?, ?, '{}') ON CONFLICT DO NOTHING");
    while (resultsInjects.next()) {
      Statement statement2 = connection.createStatement();
      String injectType = resultsInjects.getString("inject_type");
      String injectContract = resultsInjects.getString("inject_contract");
      String injectorId = injectType;
      switch (injectType) {
        case "openex_email":
        case "openaev_email":
          injectorId = "41b4dd55-5bd1-4614-98cd-9e3770753306";
          break;
        case "openex_manual":
        case "openaev_manual":
          injectorId = "6981a39d-e219-4016-a235-cf7747994abc";
          break;
        case "openex_mastodon":
        case "openaev_mastodon":
          injectorId = "37cd1743-8975-43c0-837c-f99970142e72";
          break;
        case "openex_ovh_sms":
        case "openaev_ovh_sms":
          injectorId = "e5aefbca-cf8f-4a57-9384-0503a8ffc22f";
          break;
        case "openex_lade":
        case "openaev_lade":
          injectorId = "0097265b-0515-48a5-9bff-71d0f375fcc4";
          break;
      }
      statement2.executeUpdate(
          "INSERT INTO injectors (injector_id, injector_name, injector_type) VALUES ('"
              + injectorId
              + "', '"
              + injectType
              + "','"
              + injectType
              + "') ON CONFLICT DO NOTHING");
      if (injectorId == null) {
        throw new Exception("An error occurred");
      }
      statement2.executeUpdate(
          "INSERT INTO injectors_contracts (injector_contract_id, injector_id, injector_contract_labels, injector_contract_content) VALUES ('"
              + injectContract
              + "', '"
              + injectorId
              + "','{\"en=>"
              + injectType
              + "\"}', '{}') ON CONFLICT DO NOTHING");
      statement2.close();
    }
    statement.execute(
        "ALTER TABLE injects RENAME COLUMN inject_contract TO inject_injector_contract;");
    statement.execute(
        "ALTER TABLE injects ADD CONSTRAINT injector_contract_fk FOREIGN KEY (inject_injector_contract) REFERENCES injectors_contracts(injector_contract_id) ON DELETE SET NULL;");
  }
}
