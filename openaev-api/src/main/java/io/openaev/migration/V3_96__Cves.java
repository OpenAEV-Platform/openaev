package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_96__Cves extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // --- CVEs table ---
      stmt.execute(
          """
          CREATE TABLE cves (
            cve_id varchar(255) NOT NULL CONSTRAINT cves_pkey PRIMARY KEY ,
            cve_external_id VARCHAR(255) NOT NULL UNIQUE,
            cve_source_identifier VARCHAR(255),
            cve_published TIMESTAMPTZ,
            cve_description TEXT,
            cve_vuln_status VARCHAR(255) DEFAULT 'ANALYZED',
            cve_cvss DECIMAL(3,1) CONSTRAINT chk_cvss_range CHECK (cve_cvss >= 0.0 AND cve_cvss <= 10.0),
            cve_cisa_exploit_add TIMESTAMPTZ,
            cve_cisa_action_due TIMESTAMPTZ,
            cve_cisa_required_action TEXT,
            cve_cisa_vulnerability_name TEXT,
            cve_remediation TEXT,
            cve_created_at TIMESTAMPTZ DEFAULT now(),
            cve_updated_at TIMESTAMPTZ DEFAULT now()
          );
      """);

      stmt.execute("CREATE INDEX idx_cves_cvss ON cves(cve_cvss);");
      stmt.execute("CREATE INDEX idx_cves_published ON cves(cve_published);");

      // --- CWEs table ---
      stmt.execute(
          """
          CREATE TABLE cwes (
            cwe_id VARCHAR(255) NOT NULL CONSTRAINT cwes_pkey PRIMARY KEY ,
            cwe_external_id VARCHAR(255) NOT NULL UNIQUE,
            cwe_source VARCHAR(255),
            cwe_created_at TIMESTAMPTZ DEFAULT now(),
            cwe_updated_at TIMESTAMPTZ DEFAULT now()
          );
      """);

      // --- Join table: CVEs ↔ CWEs ---
      stmt.execute(
          """
          CREATE TABLE cves_cwes (
            cve_id VARCHAR(255) NOT NULL,
            cwe_id VARCHAR(255) NOT NULL,
            PRIMARY KEY (cve_id, cwe_id),
            CONSTRAINT fk_cves_cwes_cve FOREIGN KEY (cve_id) REFERENCES cves(cve_id) ON DELETE CASCADE,
            CONSTRAINT fk_cves_cwes_cwe FOREIGN KEY (cwe_id) REFERENCES cwes(cwe_id) ON DELETE CASCADE
          );
      """);

      stmt.execute("CREATE INDEX idx_cves_cwes_cve_id ON cves_cwes(cve_id);");
      stmt.execute("CREATE INDEX idx_cves_cwes_cwe_id ON cves_cwes(cwe_id);");

      // --- Join table: CVE ↔ Reference URLs ---
      stmt.execute(
          """
          CREATE TABLE cve_reference_urls (
            cve_id VARCHAR(255) NOT NULL,
            cve_reference_url TEXT NOT NULL,
            PRIMARY KEY (cve_id, cve_reference_url),
            CONSTRAINT fk_cve_refurl FOREIGN KEY (cve_id) REFERENCES cves(cve_id) ON DELETE CASCADE
          );
      """);

      stmt.execute("CREATE INDEX idx_cve_reference_urls_cve_id ON cve_reference_urls(cve_id);");
    }
  }
}
