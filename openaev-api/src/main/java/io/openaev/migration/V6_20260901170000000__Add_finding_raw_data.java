package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code finding_raw_data}: the full, untouched OCSF Detection Finding JSON as emitted by
 * Prowler (see {@code OCSFOutputProcessor}'s class-level javadoc - the Prowler injector never
 * transforms this payload). Populated only by {@code OCSFOutputProcessor#enrichFinding}, left NULL
 * for every other finding type, so users can inspect exactly what the scanner reported on the
 * Finding detail page instead of only the handful of fields OpenAEV extracts.
 */
@Component
public class V6_20260901170000000__Add_finding_raw_data extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE findings
                  ADD COLUMN IF NOT EXISTS finding_raw_data TEXT;
              """);
    }
  }
}
