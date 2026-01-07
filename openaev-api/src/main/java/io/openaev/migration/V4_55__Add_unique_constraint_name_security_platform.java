package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_55__Add_unique_constraint_name_security_platform extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      try {
        // 1. Create temp mapping table: old_id -> new_id (case-insensitive)
        stmt.execute(
            """
            CREATE TEMP TABLE temp_security_platform_mapping AS
            SELECT a.asset_id AS old_id,
                   c.canonical_id AS new_id,
                   c.asset_name AS canonical_name
            FROM assets a
            JOIN (
                SELECT LOWER(asset_name) AS asset_name_norm,
                       security_platform_type,
                       MIN(asset_id) AS canonical_id,
                       MIN(asset_name) AS asset_name
                FROM assets
                WHERE asset_type = 'SecurityPlatform'
                GROUP BY LOWER(asset_name), security_platform_type
            ) c
            ON LOWER(a.asset_name) = c.asset_name_norm
            AND a.security_platform_type = c.security_platform_type;
        """);

        // 2. Update assets_tags safely (drop PK, update, deduplication, recreate PK)
        stmt.execute("ALTER TABLE assets_tags DROP CONSTRAINT IF EXISTS assets_tags_pkey;");
        stmt.execute(
            """
            UPDATE assets_tags at
            SET asset_id = m.new_id
            FROM temp_security_platform_mapping m
            WHERE at.asset_id = m.old_id
              AND m.old_id <> m.new_id;
        """);
        stmt.execute(
            """
            DELETE FROM assets_tags a
            USING assets_tags b
            WHERE a.asset_id = b.asset_id
              AND a.tag_id = b.tag_id
              AND a.ctid > b.ctid;
        """);
        stmt.execute(
            """
            ALTER TABLE assets_tags
            ADD CONSTRAINT assets_tags_pkey PRIMARY KEY (asset_id, tag_id);
        """);

        // 3. Update injects_expectations_traces
        stmt.execute(
            """
            UPDATE injects_expectations_traces t
            SET inject_expectation_trace_source_id = m.new_id
            FROM temp_security_platform_mapping m
            WHERE t.inject_expectation_trace_source_id = m.old_id
              AND m.old_id <> m.new_id;
        """);

        // 4. Update collectors
        stmt.execute(
            """
            UPDATE collectors c
            SET collector_security_platform = m.new_id
            FROM temp_security_platform_mapping m
            WHERE c.collector_security_platform = m.old_id
              AND m.old_id <> m.new_id;
        """);

        // 5. Update inject_expectation_results JSON (add sourcePlatform as string, nullable)
        stmt.execute(
            """
               UPDATE injects_expectations ie
               SET inject_expectation_results = sub.new_results::json
               FROM (
                   SELECT
                       ie2.inject_expectation_id,
                       jsonb_agg(f.elem ORDER BY f.score DESC) AS new_results
                   FROM injects_expectations ie2
                   CROSS JOIN LATERAL (
                       SELECT elem, score FROM (
                           SELECT DISTINCT ON (COALESCE(m.new_id, a.asset_id))
                               jsonb_set(
                                   r.elem,
                                   '{sourceId}',
                                   to_jsonb(COALESCE(m.new_id, a.asset_id)::text),
                                   true
                               ) || jsonb_set(
                                   r.elem,
                                   '{sourceName}',
                                   to_jsonb(a.asset_name),
                                   true
                               ) AS elem,
                               (r.elem->>'score')::numeric AS score
                           FROM jsonb_array_elements(ie2.inject_expectation_results::jsonb) r(elem)
                           JOIN assets a
                             ON a.asset_id::text = r.elem->>'sourceId'
                            AND a.asset_type = 'SecurityPlatform'
                           LEFT JOIN temp_security_platform_mapping m
                             ON m.old_id = a.asset_id
                           ORDER BY COALESCE(m.new_id, a.asset_id), score DESC
                       ) deduplicated

                       UNION ALL

                       -- Step 2: keep all non-SecurityPlatform results
                       SELECT
                           r.elem AS elem,
                           (r.elem->>'score')::numeric AS score
                       FROM jsonb_array_elements(ie2.inject_expectation_results::jsonb) r(elem)
                       LEFT JOIN assets a
                         ON a.asset_id::text = r.elem->>'sourceId'
                        AND a.asset_type = 'SecurityPlatform'
                       WHERE a.asset_id IS NULL
                   ) f
                   GROUP BY ie2.inject_expectation_id
               ) sub
               WHERE ie.inject_expectation_id = sub.inject_expectation_id;
            """);

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

        // 6. Delete duplicate SecurityPlatform assets
        stmt.execute(
            """
            DELETE FROM assets
            WHERE asset_id IN (
                SELECT old_id
                FROM temp_security_platform_mapping
                WHERE old_id <> new_id
            );
        """);

        // 7. Add unique index to prevent future duplicates
        stmt.execute(
            """
            CREATE UNIQUE INDEX unique_security_platform_name_type_ci_idx
            ON assets (
                lower(asset_name::text),
                asset_type,
                security_platform_type
            )
            WHERE asset_type::text = 'SecurityPlatform';
        """);

        // 8. Cleanup
        stmt.execute(
            """
            DROP TABLE temp_security_platform_mapping;
        """);

      } finally {
        stmt.execute(
            """
            DROP TABLE IF EXISTS temp_security_platform_mapping;
        """);
      }
    }
  }
}
