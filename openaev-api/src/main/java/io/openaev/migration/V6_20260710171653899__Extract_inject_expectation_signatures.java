package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260710171653899__Extract_inject_expectation_signatures extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                  CREATE TABLE IF NOT EXISTS injects_expectations_signatures (
                      inject_expectation_signature_inject_expectation_id VARCHAR(255) NOT NULL,
                      inject_expectation_signature_type VARCHAR NOT NULL,
                      inject_expectation_signature_value VARCHAR NOT NULL,
                      inject_expectation_signature_created_at TIMESTAMP NOT NULL DEFAULT now(),
                      CONSTRAINT pk_injects_expectations_signatures PRIMARY KEY (
                          inject_expectation_signature_inject_expectation_id,
                          inject_expectation_signature_type,
                          inject_expectation_signature_value
                      )
                  );
                  """);

      statement.execute(
          """
                  CREATE INDEX IF NOT EXISTS idx_injects_expectations_signatures_expectation_id
                      ON injects_expectations_signatures (inject_expectation_signature_inject_expectation_id);
                  """);

      statement.execute(
          """
                  DO $$
                  BEGIN
                      IF NOT EXISTS (
                          SELECT 1
                          FROM pg_constraint
                          WHERE conname = 'fk_injects_expectations_signatures_expectation'
                      ) THEN
                          ALTER TABLE injects_expectations_signatures
                              ADD CONSTRAINT fk_injects_expectations_signatures_expectation
                              FOREIGN KEY (inject_expectation_signature_inject_expectation_id)
                              REFERENCES injects_expectations(inject_expectation_id)
                              ON DELETE CASCADE;
                      END IF;
                  END $$;
                  """);

      statement.execute(
          """
                  DO $$
                  BEGIN
                      IF EXISTS (
                          SELECT 1
                          FROM information_schema.columns
                          WHERE table_name = 'injects_expectations'
                            AND table_schema = current_schema()
                            AND column_name = 'inject_expectation_signatures'
                      ) THEN
                          INSERT INTO injects_expectations_signatures (
                              inject_expectation_signature_inject_expectation_id,
                              inject_expectation_signature_type,
                              inject_expectation_signature_value,
                              inject_expectation_signature_created_at
                          )
                          SELECT
                              raw.inject_expectation_id,
                              raw.signature_type,
                              raw.signature_value,
                              raw.inject_expectation_created_at
                          FROM (
                              SELECT
                                  ie.inject_expectation_id::uuid AS inject_expectation_id,
                                  signature->>'type' AS signature_type,
                                  signature->>'value' AS signature_value,
                                  ie.inject_expectation_created_at,
                                  ordinality
                              FROM injects_expectations ie
                              CROSS JOIN LATERAL jsonb_array_elements(
                                  CASE
                                      WHEN ie.inject_expectation_signatures IS NULL THEN '[]'::jsonb
                                      WHEN jsonb_typeof(ie.inject_expectation_signatures::jsonb) = 'array'
                                          THEN ie.inject_expectation_signatures::jsonb
                                      ELSE '[]'::jsonb
                                  END
                              ) WITH ORDINALITY AS signature(signature, ordinality)
                              WHERE ie.inject_expectation_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
                          ) raw
                          WHERE COALESCE(raw.signature_type, '') <> ''
                            AND COALESCE(raw.signature_value, '') <> ''
                          ORDER BY raw.inject_expectation_id, raw.signature_type, raw.ordinality
                          ON CONFLICT (
                              inject_expectation_signature_inject_expectation_id,
                              inject_expectation_signature_type,
                              inject_expectation_signature_value
                          ) DO NOTHING;

                          ALTER TABLE injects_expectations
                              DROP COLUMN IF EXISTS inject_expectation_signatures;
                      END IF;
                  END $$;
                  """);
    }
  }
}

// ROLLBACK
//
// ALTER TABLE injects_expectations
//     ADD COLUMN IF NOT EXISTS inject_expectation_signatures jsonb;
//
// UPDATE injects_expectations ie
// SET inject_expectation_signatures = sub.signatures
// FROM (
//     SELECT
//         ies.inject_expectation_signature_inject_expectation_id::text AS inject_expectation_id,
//         jsonb_agg(
//             jsonb_build_object(
//                 'type', ies.inject_expectation_signature_type,
//                 'value', ies.inject_expectation_signature_value
//             )
//             ORDER BY ies.inject_expectation_signature_created_at
//         ) AS signatures
//     FROM injects_expectations_signatures ies
//     GROUP BY ies.inject_expectation_signature_inject_expectation_id
// ) sub
// WHERE ie.inject_expectation_id = sub.inject_expectation_id;
//
// DROP TABLE IF EXISTS injects_expectations_signatures;
