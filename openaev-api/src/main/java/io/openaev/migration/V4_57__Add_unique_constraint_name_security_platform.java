package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_57__Add_unique_constraint_name_security_platform extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // 1. Rename securities platforms with same name and type
      stmt.execute(
          """
                  WITH ranked AS (
                      SELECT
                          asset_id,
                          asset_name,
                          security_platform_type,
                          ROW_NUMBER() OVER (
                              PARTITION BY LOWER(asset_name), security_platform_type
                              ORDER BY asset_created_at
                          ) AS rn
                      FROM assets
                      WHERE asset_type = 'SecurityPlatform'
                  )
                  UPDATE assets a
                  SET asset_name = a.asset_name || '_' || (ranked.rn - 1)
                  FROM ranked
                  WHERE a.asset_id = ranked.asset_id
                    AND ranked.rn > 1;
              """);

      // 2. Update inject_expectation_results to reflect renamed security platforms
      stmt.execute(
          """
                 UPDATE injects_expectations ie
                 SET inject_expectation_results = sub.new_results
                 FROM (
                     SELECT
                         ie2.inject_expectation_id,
                         jsonb_agg(
                             CASE
                                 WHEN a.asset_id IS NOT NULL THEN
                                     jsonb_set(
                                         r.elem,
                                         '{sourceName}',
                                         to_jsonb(a.asset_name),
                                         true
                                     )
                                 ELSE
                                     r.elem
                             END
                         ) AS new_results
                     FROM injects_expectations ie2
                     CROSS JOIN LATERAL jsonb_array_elements(ie2.inject_expectation_results::jsonb) r(elem)
                     LEFT JOIN assets a
                       ON a.asset_id::text = r.elem->>'sourceId'
                      AND a.asset_type = 'SecurityPlatform'
                     GROUP BY ie2.inject_expectation_id
                 ) sub
                 WHERE ie.inject_expectation_id = sub.inject_expectation_id;
              """);

      // 3. Update inject_expectation_results to add sourcePlatform field
      stmt.execute(
          """
                 UPDATE injects_expectations ie
                  SET inject_expectation_results = sub.new_results::json
                  FROM (
                  SELECT ie2.inject_expectation_id,
                  jsonb_agg(
                  CASE
                  -- SecurityPlatform sourceType
                  WHEN r.elem->>'sourceType' = 'security-platform' AND a.asset_id IS NOT NULL THEN
                  jsonb_set(r.elem, '{sourcePlatform}', to_jsonb(a.security_platform_type), true)
                  -- Collector sourceType
                  WHEN r.elem->>'sourceType' = 'collector' AND c.collector_id IS NOT NULL AND sp.asset_id IS NOT NULL THEN
                  jsonb_set(r.elem, '{sourcePlatform}', to_jsonb(sp.security_platform_type), true)
                  -- Other sourceType
                  ELSE
                  jsonb_set(r.elem, '{sourcePlatform}', 'null'::jsonb, true)
                  END
                  ) AS new_results
                  FROM injects_expectations ie2
                  CROSS JOIN LATERAL jsonb_array_elements(ie2.inject_expectation_results::jsonb) r(elem)
                  -- Join assets if sourceType = SecurityPlatform
                  LEFT JOIN assets a
                  ON r.elem->>'sourceType' = 'security-platform'
                  AND a.asset_id::text = r.elem->>'sourceId'
                  -- Join collectors
                  LEFT JOIN collectors c
                  ON r.elem->>'sourceType' = 'collector'
                  AND c.collector_id::text = r.elem->>'sourceId'
                  -- Join collector's security platform
                  LEFT JOIN assets sp
                  ON c.collector_security_platform = sp.asset_id
                  GROUP BY ie2.inject_expectation_id
                  ) sub
                  WHERE ie.inject_expectation_id = sub.inject_expectation_id;
              """);

      // 4. Add unique index to prevent future duplicates
      stmt.execute(
          """
                  CREATE UNIQUE INDEX unique_security_platform_name_type_ci_idx
                  ON assets (
                      lower(asset_name::text),
                      security_platform_type
                  )
                  WHERE asset_type::text = 'SecurityPlatform';
              """);
    }
  }
}
