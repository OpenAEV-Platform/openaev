package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_29__Add_tables_xls_mappers extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Create table
    select.execute(
        """
          CREATE TABLE import_mappers (
            mapper_id UUID NOT NULL CONSTRAINT import_mappers_pkey PRIMARY KEY,
            mapper_name VARCHAR(255) NOT NULL,
            mapper_inject_type_column VARCHAR(255) NOT NULL,
            mapper_created_at TIMESTAMP DEFAULT now(),
            mapper_updated_at TIMESTAMP DEFAULT now()

          );
          CREATE INDEX idx_import_mappers ON import_mappers(mapper_id);
     """);

    select.execute(
        """
          CREATE TABLE inject_importers (
            importer_id UUID NOT NULL CONSTRAINT inject_importers_pkey PRIMARY KEY,
            importer_mapper_id UUID NOT NULL
              CONSTRAINT inject_importers_mapper_id_fkey REFERENCES import_mappers(mapper_id) ON DELETE SET NULL,
            importer_import_type_value VARCHAR(255) NOT NULL,
            importer_injector_contract_id VARCHAR(255) NOT NULL
              CONSTRAINT inject_importers_injector_contract_id_fkey REFERENCES injectors_contracts(injector_contract_id) ON DELETE SET NULL,
            importer_created_at TIMESTAMP DEFAULT now(),
            importer_updated_at TIMESTAMP DEFAULT now()
          );
          CREATE INDEX idx_inject_importers ON inject_importers(importer_id);
     """);

    select.execute(
        """
          CREATE TABLE rule_attributes (
            attribute_id UUID NOT NULL CONSTRAINT rule_attributes_pkey PRIMARY KEY,
            attribute_inject_importer_id UUID NOT NULL
              CONSTRAINT rule_attributes_importer_id_fkey REFERENCES inject_importers(importer_id) ON DELETE SET NULL,
            attribute_name varchar(255) not null,
            attribute_columns varchar(255),
            attribute_default_value varchar(255),
            attribute_additional_config HSTORE,
            attribute_created_at TIMESTAMP DEFAULT now(),
            attribute_updated_at TIMESTAMP DEFAULT now()
          );
          CREATE INDEX idx_rule_attributes on rule_attributes(attribute_id);
     """);

    select.execute(
        """
          ALTER TABLE injectors_contracts ADD COLUMN injector_contract_import_available BOOLEAN NOT NULL DEFAULT FALSE;
     """);

    select.execute(
        """
          UPDATE injectors_contracts SET injector_contract_import_available = true WHERE injector_contract_labels -> 'en' LIKE ANY(ARRAY['%SMS%', '%Send%mail%']);
     """);
  }
}
