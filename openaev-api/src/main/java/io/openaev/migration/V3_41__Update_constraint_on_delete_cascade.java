package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_41__Update_constraint_on_delete_cascade extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        "ALTER TABLE inject_importers DROP CONSTRAINT inject_importers_injector_contract_id_fkey;");
    select.execute("ALTER TABLE rule_attributes DROP CONSTRAINT rule_attributes_importer_id_fkey;");

    // Add the new foreign key constraint with ON DELETE CASCADE
    select.execute(
        "ALTER TABLE inject_importers ADD CONSTRAINT inject_importers_injector_contract_id_fkey FOREIGN KEY (importer_injector_contract_id) REFERENCES injectors_contracts(injector_contract_id) ON DELETE CASCADE;");
    select.execute(
        "ALTER TABLE rule_attributes ADD CONSTRAINT rule_attributes_importer_id_fkey FOREIGN KEY (attribute_inject_importer_id) REFERENCES inject_importers(importer_id) ON DELETE CASCADE;");

    // Optionally, you can reindex if necessary (usually not required just for FK changes)
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_importers_injector_contracts ON inject_importers(importer_injector_contract_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_rule_attributes_inject_importers ON rule_attributes(attribute_inject_importer_id);");
  }
}
