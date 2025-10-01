package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_74__Add_Output_parser extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              CREATE TABLE output_parsers (
                      output_parser_id varchar(255) NOT NULL PRIMARY KEY,
                      output_parser_mode VARCHAR(50) NOT NULL,
                      output_parser_type VARCHAR(50) NOT NULL,
                      output_parser_payload_id VARCHAR(255) NOT NULL,
                      output_parser_created_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      output_parser_updated_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      CONSTRAINT output_parser_payload_id_fk
                          FOREIGN KEY (output_parser_payload_id)
                          REFERENCES payloads(payload_id)
                          ON DELETE CASCADE
                  );
              """);

      statement.execute(
          """
                  CREATE TABLE contract_output_elements (
                      contract_output_element_id varchar(255) NOT NULL PRIMARY KEY,
                      contract_output_element_is_finding BOOLEAN DEFAULT true,
                      contract_output_element_rule TEXT NOT NULL,
                      contract_output_element_name VARCHAR(50) NOT NULL,
                      contract_output_element_key VARCHAR(255) NOT NULL,
                      contract_output_element_type VARCHAR(50) NOT NULL,
                      contract_output_element_output_parser_id varchar(255) NOT NULL,
                      contract_output_element_created_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      contract_output_element_updated_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      CONSTRAINT contract_output_element_output_parser_id_fk
                          FOREIGN KEY (contract_output_element_output_parser_id)
                          REFERENCES output_parsers(output_parser_id)
                          ON DELETE CASCADE,
                      UNIQUE (contract_output_element_key, contract_output_element_output_parser_id)
                  );
              """);

      statement.execute(
          """
                  CREATE TABLE regex_groups (
                      regex_group_id varchar(255) NOT NULL PRIMARY KEY,
                      regex_group_field VARCHAR(50) NOT NULL,
                      regex_group_index_values VARCHAR(50) NOT NULL,
                      regex_group_contract_output_element_id varchar(255) NOT NULL,
                      regex_group_created_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      regex_group_updated_at TIMESTAMP(0) WITH TIME ZONE DEFAULT now(),
                      CONSTRAINT regex_group_contract_output_element_id_fk
                          FOREIGN KEY (regex_group_contract_output_element_id)
                          REFERENCES contract_output_elements(contract_output_element_id)
                          ON DELETE CASCADE
                  );
              """);

      statement.execute(
          """
                  CREATE TABLE contract_output_elements_tags (
                     contract_output_element_id varchar(255) NOT NULL,
                     tag_id VARCHAR(255) NOT NULL,
                     PRIMARY KEY (contract_output_element_id, tag_id),
                     CONSTRAINT contract_output_element_id_fk
                         FOREIGN KEY (contract_output_element_id)
                         REFERENCES contract_output_elements(contract_output_element_id)
                         ON DELETE CASCADE,
                     CONSTRAINT tag_id_fk
                         FOREIGN KEY (tag_id)
                         REFERENCES tags(tag_id)
                         ON DELETE CASCADE
                 );
                 CREATE INDEX idx_contract_output_elements_tags_contract_output_elements
                 ON contract_output_elements_tags (contract_output_element_id);

                 CREATE INDEX idx_contract_output_elements_tags_tag
                 ON contract_output_elements_tags (tag_id);
              """);

      statement.execute(
          """
               CREATE TABLE findings_tags (
                   finding_id VARCHAR(255) NOT NULL,
                   tag_id VARCHAR(255) NOT NULL,
                   PRIMARY KEY (finding_id, tag_id),
                   CONSTRAINT finding_id_fk
                       FOREIGN KEY (finding_id)
                       REFERENCES findings(finding_id)
                       ON DELETE CASCADE,
                   CONSTRAINT tag_id_fk
                       FOREIGN KEY (tag_id)
                       REFERENCES tags(tag_id)
                       ON DELETE CASCADE
               );
               CREATE INDEX idx_findings_tags_finding ON findings_tags (finding_id);
               CREATE INDEX idx_findings_tags_tag ON findings_tags (tag_id);
              """);
    }
  }
}
