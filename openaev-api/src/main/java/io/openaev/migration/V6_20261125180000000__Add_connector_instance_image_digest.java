package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the digest of the image a managed connector instance runs, reported by XTM Composer in its
 * health check (algo:hex, for example sha256 followed by 64 hex characters). Nullable: unknown
 * until a composer that reports it sends a health check.
 */
@Component
public class V6_20261125180000000__Add_connector_instance_image_digest extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE connector_instances"
              + " ADD COLUMN IF NOT EXISTS connector_instance_image_digest VARCHAR(255)");
    }
  }
}
