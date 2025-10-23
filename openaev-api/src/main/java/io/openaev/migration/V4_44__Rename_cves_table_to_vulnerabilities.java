package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_44__Rename_cves_table_to_vulnerabilities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // --- Rename main table ---
      stmt.execute(
          """
                    ALTER TABLE cves RENAME TO vulnerabilities;
                    """);
      //
      // --- Rename columns in vulnerabilities table ---
      stmt.execute(
          """
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_id TO vulnerability_id;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_external_id TO vulnerability_external_id;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_source_identifier TO vulnerability_source_identifier;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_published TO vulnerability_published;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_description TO vulnerability_description;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_vuln_status TO vulnerability_vuln_status;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_cvss_v31 TO vulnerability_cvss_v31;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_cisa_exploit_add TO vulnerability_cisa_exploit_add;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_cisa_action_due TO vulnerability_cisa_action_due;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_cisa_required_action TO vulnerability_cisa_required_action;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_cisa_vulnerability_name TO vulnerability_cisa_vulnerability_name;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_remediation TO vulnerability_remediation;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_created_at TO vulnerability_created_at;
                    ALTER TABLE vulnerabilities RENAME COLUMN cve_updated_at TO vulnerability_updated_at;
                    """);

      // --- Rename indexes of the vulnerabilities table ---
      stmt.execute(
          """
                    ALTER INDEX idx_cves_cvss RENAME TO idx_vulnerabilities_cvss;
                    ALTER INDEX idx_cves_published RENAME TO idx_vulnerabilities_published;
                    """);

      // --- Rename join table CVEs ↔ CWEs ---
      stmt.execute(
          """
                    ALTER TABLE cves_cwes RENAME TO vulnerabilities_cwes;
                    ALTER TABLE vulnerabilities_cwes RENAME COLUMN cve_id TO vulnerability_id;
                    """);

      // --- Rename indexes of the join table ---
      stmt.execute(
          """
                    ALTER INDEX idx_cves_cwes_cve_id RENAME TO idx_vulnerabilities_cwes_vulnerability_id;
                    ALTER INDEX idx_cves_cwes_cwe_id RENAME TO idx_vulnerabilities_cwes_cwe_id;
                    """);

      // --- Rename reference URL table ---
      stmt.execute(
          """
                    ALTER TABLE cve_reference_urls RENAME TO vulnerability_reference_urls;
                    ALTER TABLE vulnerability_reference_urls RENAME COLUMN cve_id TO vulnerability_id;
                    ALTER TABLE vulnerability_reference_urls RENAME COLUMN cve_reference_url TO vulnerability_reference_url;
                    """);

      // --- Rename index of the reference URL table ---
      stmt.execute(
          """
                    ALTER INDEX idx_cve_reference_urls_cve_id RENAME TO idx_vulnerability_reference_urls_vulnerability_id;
                    """);
    }
  }
}

// ROLLBACK SCRIPT
// BEGIN;
//
// -- ============================================================================
//        -- 1. Rollback of the table vulnerability_reference_urls to cve_reference_urls
// -- ============================================================================
//
//        -- Rename the index of the reference URL table
// ALTER INDEX IF EXISTS idx_vulnerability_reference_urls_vulnerability_id
// RENAME TO idx_cve_reference_urls_cve_id;
//
// -- Rename the columns of the reference URL table
// ALTER TABLE IF EXISTS vulnerability_reference_urls
// RENAME COLUMN vulnerability_reference_url TO cve_reference_url;
//
// ALTER TABLE IF EXISTS vulnerability_reference_urls
// RENAME COLUMN vulnerability_id TO cve_id;
//
// -- Rename the reference URL table
// ALTER TABLE IF EXISTS vulnerability_reference_urls
// RENAME TO cve_reference_urls;
//
// -- ============================================================================
//        -- 2. Rollback of the join table vulnerabilities_cwes to cves_cwes
// -- ============================================================================
//
//        -- Rename the indexes of the join table
// ALTER INDEX IF EXISTS idx_vulnerabilities_cwes_cwe_id
// RENAME TO idx_cves_cwes_cwe_id;
//
// ALTER INDEX IF EXISTS idx_vulnerabilities_cwes_vulnerability_id
// RENAME TO idx_cves_cwes_cve_id;
//
// -- Rename the column in the join table
// ALTER TABLE IF EXISTS vulnerabilities_cwes
// RENAME COLUMN vulnerability_id TO cve_id;
//
// -- Rename the join table
// ALTER TABLE IF EXISTS vulnerabilities_cwes
// RENAME TO cves_cwes;
//
// -- ============================================================================
//        -- 3. Rollback of the main table vulnerabilities to cves
// -- ============================================================================
//
//        -- Rename the indexes of the main table
// ALTER INDEX IF EXISTS idx_vulnerabilities_published
// RENAME TO idx_cves_published;
//
// ALTER INDEX IF EXISTS idx_vulnerabilities_cvss
// RENAME TO idx_cves_cvss;
//
// -- Rename all columns in the vulnerabilities table back to their original names
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_updated_at TO cve_updated_at;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_created_at TO cve_created_at;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_remediation TO cve_remediation;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_cisa_vulnerability_name TO cve_cisa_vulnerability_name;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_cisa_required_action TO cve_cisa_required_action;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_cisa_action_due TO cve_cisa_action_due;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_cisa_exploit_add TO cve_cisa_exploit_add;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_cvss_v31 TO cve_cvss_v31;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_vuln_status TO cve_vuln_status;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_description TO cve_description;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_published TO cve_published;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_source_identifier TO cve_source_identifier;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_external_id TO cve_external_id;
//
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME COLUMN vulnerability_id TO cve_id;
//
// -- Rename the main table
// ALTER TABLE IF EXISTS vulnerabilities
// RENAME TO cves;
//
// -- ============================================================================
//        -- Validation and commit
// -- ============================================================================
//
//        -- Verify that the tables have been correctly renamed
// DO $$
// BEGIN
// -- Verify the existence of the table cves
// IF NOT EXISTS (SELECT 1 FROM information_schema.tables
//        WHERE table_schema = 'public'
//        AND table_name = 'cves') THEN
// RAISE EXCEPTION 'Error: The table cves has not been correctly created';
// END IF;
//
// -- Verify the existence of the table cves_cwes
// IF NOT EXISTS (SELECT 1 FROM information_schema.tables
//        WHERE table_schema = 'public'
//        AND table_name = 'cves_cwes') THEN
// RAISE EXCEPTION 'Error: The table cves_cwes has not been correctly created';
// END IF;
//
// -- Verify the existence of the table cve_reference_urls
// IF NOT EXISTS (SELECT 1 FROM information_schema.tables
//        WHERE table_schema = 'public'
//        AND table_name = 'cve_reference_urls') THEN
// RAISE EXCEPTION 'Error: The table cve_reference_urls has not been correctly created';
// END IF;
//
// RAISE NOTICE 'Rollback successfully performed. All tables have been restored.';
// END $$;
//
// -- If everything is OK, commit the transaction
// COMMIT;
