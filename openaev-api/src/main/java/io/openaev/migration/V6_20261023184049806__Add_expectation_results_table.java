package io.openaev.migration;

import java.sql.Statement;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V6_20261023184049806__Add_expectation_results_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // some columns are null to support historical results
      statement.execute(
          """
        CREATE TABLE IF NOT EXISTS inject_expectation_results(
            inject_expectation_result_id VARCHAR(255) PRIMARY KEY,
            inject_expectation_result_source_id VARCHAR(255) NULL,
            inject_expectation_result_source_type VARCHAR(255) NULL,
            inject_expectation_result_source_name VARCHAR(255) NULL,
            inject_expectation_result_source_asset_id VARCHAR(255) NULL,
            inject_expectation_result_source_platform VARCHAR(255) NULL,
            inject_expectation_result_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
            inject_expectation_result_result TEXT NULL, -- support provisional results
            inject_expectation_result_score NUMERIC NULL, -- support provisional results
            inject_expectation_result_metadata JSONB,
            inject_expectation_result_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
            inject_expectation_result_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
            inject_expectation_result_inject_expectation_id VARCHAR(255) NOT NULL REFERENCES injects_expectations(inject_expectation_id) ON DELETE CASCADE
        );
        """);

      statement.execute(
          """
        CREATE INDEX IF NOT EXISTS idx_inject_expectation_results_expectation
          ON inject_expectation_results (inject_expectation_result_inject_expectation_id);
        """);

      // Legacy results carry no date: the ones built by V2_75 only hold 'result', and until
      // mid-2024
      // the challenge and channel validations stored their timestamp in 'result' too. An explicit
      // NULL bypasses the column default, so the date falls back to the expectation's own dates.
      // Empty strings and non-array values are skipped rather than failing the cast.
      statement.execute(
          """
        WITH results AS (
                SELECT inject_expectation_id,
                       inject_expectation_updated_at,
                       inject_expectation_created_at,
                       json_array_elements(inject_expectation_results) AS res
                FROM injects_expectations
                WHERE json_typeof(inject_expectation_results) = 'array'
        )
        INSERT INTO inject_expectation_results (
          inject_expectation_result_id,
          inject_expectation_result_source_id,
          inject_expectation_result_source_type,
          inject_expectation_result_source_name,
          inject_expectation_result_source_asset_id,
          inject_expectation_result_source_platform,
          inject_expectation_result_date,
          inject_expectation_result_result,
          inject_expectation_result_score,
          inject_expectation_result_metadata,
          inject_expectation_result_created_at,
          inject_expectation_result_updated_at,
          inject_expectation_result_inject_expectation_id
        )
        SELECT gen_random_uuid(),
          res->>'sourceId',
          res->>'sourceType',
          res->>'sourceName',
          res->>'sourceAssetId',
          res->>'sourcePlatform',
          COALESCE(
            NULLIF(res->>'date', '')::timestamptz,
            inject_expectation_updated_at,
            inject_expectation_created_at,
            NOW()
          ),
          res->>'result',
          NULLIF(res->>'score', '')::numeric,
          res->'metadata',
          NOW(),
          NOW(),
          inject_expectation_id
        FROM results;
        """);

      statement.execute(
          """
        ALTER TABLE IF EXISTS injects_expectations
        DROP COLUMN IF EXISTS inject_expectation_results;
        """);

      statement.execute(
          """
      DELETE FROM indexing_status WHERE indexing_status_type = 'expectation-inject';
      """);
    }
  }
}
