package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code finding_cloud_provider} to {@code findings}, populated only by {@code
 * OCSFOutputProcessor} from OCSF {@code cloud.provider} (e.g. "aws", "azure", "gcp", "kubernetes").
 * Complements the cloud misconfiguration columns added by {@code
 * V6_20260810144924000__Add_finding_cloud_misconfiguration_fields}: without it, the frontend had no
 * way to label a cloud finding by its actual provider (e.g. "Cloud (AWS)") and had to fall back to
 * displaying the internal contract type name ("OCSF").
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260811073211000__Add_finding_cloud_provider extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_cloud_provider varchar(255);");
    }
  }
}
