package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_68__Rename_content_attribute extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(
                """
                    UPDATE injectors_contracts ic
                    SET injector_contract_content = CASE
                        WHEN p.command_executor = 'cmd' THEN
                            jsonb_set(
                                    ic.injector_contract_content::jsonb,
                                    '{fields}',
                                    (
                                        SELECT jsonb_agg(
                                                       CASE
                                                           WHEN field->>'key' = 'obfuscator' THEN
                                                               jsonb_set(
                                                                       field,
                                                                       '{choices}',
                                                                       (
                                                                           SELECT jsonb_agg(choice)
                                                                           FROM jsonb_array_elements(field->'choices') AS choice
                                                                           WHERE choice->>'value' != 'base64'
                                                                       )
                                                               )
                                                           ELSE field
                                                           END
                                               )
                                        FROM jsonb_array_elements(ic.injector_contract_content::jsonb->'fields') AS field
                                    )
                            )::text
                        WHEN p.command_executor = 'psh' THEN
                            jsonb_set(
                                    ic.injector_contract_content::jsonb,
                                    '{fields}',
                                    (
                                        SELECT jsonb_agg(
                                                       CASE
                                                           WHEN field->>'key' = 'obfuscator' THEN
                                                               jsonb_set(
                                                                       field,
                                                                       '{choices}',
                                                                       (
                                                                           SELECT jsonb_agg(
                                                                                          CASE
                                                                                              WHEN choice->>'value' = 'base64' THEN
                                                                                                  jsonb_set(choice, '{information}', '""'::jsonb)
                                                                                              ELSE choice
                                                                                              END
                                                                                  )
                                                                           FROM jsonb_array_elements(field->'choices') AS choice
                                                                       )
                                                               )
                                                           ELSE field
                                                           END
                                               )
                                        FROM jsonb_array_elements(ic.injector_contract_content::jsonb->'fields') AS field
                                    )
                            )::text
                        ELSE ic.injector_contract_content
                    END
                    FROM payloads p
                    WHERE ic.injector_contract_payload = p.payload_id
                      AND p.payload_type = 'Command'
                      AND EXISTS (
                        SELECT 1
                        FROM jsonb_array_elements(ic.injector_contract_content::jsonb->'fields') AS field
                        WHERE field->>'key' = 'obfuscator'
                    );
                """
            );
        }
    }
}
