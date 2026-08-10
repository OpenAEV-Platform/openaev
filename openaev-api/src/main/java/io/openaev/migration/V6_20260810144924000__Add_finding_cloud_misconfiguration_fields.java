package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds cloud misconfiguration columns to {@code findings}, populated only by {@code
 * OCSFOutputProcessor} when parsing a scanner's native OCSF ("Open Cybersecurity Schema Framework")
 * Detection Finding JSON (currently Prowler, via {@code ContractOutputType#OCSF}):
 *
 * <ul>
 *   <li>{@code finding_severity} - OCSF {@code severity} (Critical/High/Medium/Low/Informational).
 *   <li>{@code finding_resource} - the scanned cloud resource identifier ({@code
 *       resources[].uid}/{@code .arn}), distinct from {@code finding_value} which stays the
 *       human-readable check title.
 *   <li>{@code finding_cloud_account} - OCSF {@code cloud.account.uid}.
 *   <li>{@code finding_cloud_region} - OCSF {@code cloud.region}.
 *   <li>{@code finding_remediation} - OCSF {@code remediation.desc}.
 *   <li>{@code finding_compliance} - comma-joined violated compliance requirements (e.g. "CIS
 *       2.1.1, NIST 800-53").
 * </ul>
 *
 * <p>Every other {@code OutputProcessor} (CVE, Vulnerability, credentials, ...) leaves these
 * columns null - they are specific to cloud-misconfiguration findings.
 *
 * <p>Additive, idempotent, and lock-light: nullable {@code ADD COLUMN}s are metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260810144924000__Add_finding_cloud_misconfiguration_fields
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_severity varchar(255);");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_resource varchar(255);");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_cloud_account varchar(255);");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_cloud_region varchar(255);");
      statement.execute("ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_remediation text;");
      statement.execute("ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_compliance text;");
    }
  }
}
