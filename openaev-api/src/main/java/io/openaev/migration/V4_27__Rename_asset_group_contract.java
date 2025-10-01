package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_27__Rename_asset_group_contract extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String renameAssetGroupInContracts =
          """
          UPDATE injectors_contracts
          SET injector_contract_content = jsonb_set(
                  injector_contract_content::jsonb,
                  '{fields}',
                  (
                      SELECT jsonb_agg(
                                     CASE
                                         WHEN field->>'key' = 'assetgroups'
                                             THEN jsonb_set(
                                                 field,
                                                 '{key}',
                                                 '"asset_groups"'::jsonb
                                                  )
                                         ELSE field
                                         END
                                         ||
                                     CASE
                                         WHEN field ? 'mandatoryGroups'
                                             AND jsonb_typeof(field->'mandatoryGroups') = 'array'
                                             THEN jsonb_build_object(
                                                 'mandatoryGroups',
                                                 (
                                                     SELECT jsonb_agg(
                                                                    CASE
                                                                        WHEN mg = 'assetgroups' THEN 'asset_groups'
                                                                        ELSE mg
                                                                        END
                                                            )
                                                     FROM jsonb_array_elements_text(field->'mandatoryGroups') AS t(mg)
                                                 )
                                                  )
                                         ELSE '{}'::jsonb
                                         END
                             )
                      FROM jsonb_array_elements(injector_contract_content::jsonb->'fields') AS field
                  )
                                          )
          WHERE EXISTS (
              SELECT 1
              FROM jsonb_array_elements(injector_contract_content::jsonb->'fields') AS field
              WHERE field->>'key' = 'assetgroups'
                 OR (field ? 'mandatoryGroups'
                  AND jsonb_typeof(field->'mandatoryGroups') = 'array'
                  AND EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements_text(field->'mandatoryGroups') AS t(mg)
                      WHERE mg = 'assetgroups'
                  )
                  )
          );
          """;

      statement.executeUpdate(renameAssetGroupInContracts);
    }
  }
}
