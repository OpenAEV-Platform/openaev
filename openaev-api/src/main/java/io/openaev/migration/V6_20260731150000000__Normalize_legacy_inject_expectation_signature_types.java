package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rewrites legacy inject expectation signature types to their canonical names (the constants in
 * {@code ExpectationSignatureUtils}). The signature type is a free string column and early
 * injectors persisted short-form names ({@code start_time}, {@code source_ipv4}, ...). Collectors
 * validate the type against a strict enum of the canonical names and crash on the legacy values
 * when processing pending expectations, so the legacy rows must be rewritten in place.
 *
 * <p>The primary key is (expectation id, type, value): a legacy row is only rewritten when the
 * expectation does not already carry the same signature under the canonical type; remaining legacy
 * duplicates are deleted.
 */
@Component
public class V6_20260731150000000__Normalize_legacy_inject_expectation_signature_types
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE injects_expectations_signatures s
          SET inject_expectation_signature_type = m.new_type
          FROM (VALUES
            ('start_time', 'start_date'),
            ('end_time', 'end_date'),
            ('source_ipv4', 'source_ipv4_address'),
            ('source_ipv6', 'source_ipv6_address'),
            ('target_ipv4', 'target_ipv4_address'),
            ('target_ipv6', 'target_ipv6_address'),
            ('target_hostname', 'target_hostname_address')
          ) AS m(old_type, new_type)
          WHERE s.inject_expectation_signature_type = m.old_type
            AND NOT EXISTS (
              SELECT 1 FROM injects_expectations_signatures t
              WHERE t.inject_expectation_signature_inject_expectation_id =
                      s.inject_expectation_signature_inject_expectation_id
                AND t.inject_expectation_signature_type = m.new_type
                AND t.inject_expectation_signature_value = s.inject_expectation_signature_value
            );
          """);
      // Legacy rows whose canonical twin already existed on the same expectation are duplicates.
      statement.execute(
          """
          DELETE FROM injects_expectations_signatures
          WHERE inject_expectation_signature_type IN (
            'start_time', 'end_time',
            'source_ipv4', 'source_ipv6',
            'target_ipv4', 'target_ipv6',
            'target_hostname'
          );
          """);
    }
  }
}
