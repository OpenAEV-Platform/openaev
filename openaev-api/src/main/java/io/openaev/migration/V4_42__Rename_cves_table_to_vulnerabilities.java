package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_42__Rename_cves_table_to_vulnerabilities extends BaseJavaMigration {

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
