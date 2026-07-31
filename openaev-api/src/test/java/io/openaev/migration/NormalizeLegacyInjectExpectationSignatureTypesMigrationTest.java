package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the legacy signature type repair: short-form types are rewritten to the canonical {@code
 * ExpectationSignatureUtils} names, legacy rows whose canonical twin already exists are dropped
 * instead of violating the (expectation, type, value) primary key, canonical rows are left
 * untouched, and re-running the migration is a no-op.
 *
 * <p>{@code @Transactional} so the seeded rows and migration side effects roll back with the test
 * transaction.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Normalize legacy inject expectation signature types migration")
class NormalizeLegacyInjectExpectationSignatureTypesMigrationTest extends IntegrationTest {

  @Autowired
  private V6_20260731150000000__Normalize_legacy_inject_expectation_signature_types migration;

  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;

  private String persistExpectation() {
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withExpectation(injectExpectationComposer.forExpectation(expectation))
        .persist();
    entityManager.flush();
    return expectation.getId();
  }

  private void insertSignature(String expectationId, String type, String value) {
    entityManager
        .createNativeQuery(
            "INSERT INTO injects_expectations_signatures ("
                + "inject_expectation_signature_inject_expectation_id, "
                + "inject_expectation_signature_type, "
                + "inject_expectation_signature_value) VALUES (:id, :type, :value)")
        .setParameter("id", expectationId)
        .setParameter("type", type)
        .setParameter("value", value)
        .executeUpdate();
  }

  @SuppressWarnings("unchecked")
  private List<Object[]> signaturesOf(String expectationId) {
    return entityManager
        .createNativeQuery(
            "SELECT inject_expectation_signature_type, inject_expectation_signature_value "
                + "FROM injects_expectations_signatures "
                + "WHERE inject_expectation_signature_inject_expectation_id = :id "
                + "ORDER BY inject_expectation_signature_type, inject_expectation_signature_value")
        .setParameter("id", expectationId)
        .getResultList();
  }

  private void runMigration() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  @DisplayName("Rewrites every legacy type to its canonical name and keeps canonical rows intact")
  void legacy_types_are_rewritten() {
    String expectationId = persistExpectation();
    insertSignature(expectationId, "start_time", "2026-07-31T10:00:00Z");
    insertSignature(expectationId, "end_time", "2026-07-31T11:00:00Z");
    insertSignature(expectationId, "source_ipv4", "10.0.0.1");
    insertSignature(expectationId, "source_ipv6", "::1");
    insertSignature(expectationId, "target_ipv4", "10.0.0.2");
    insertSignature(expectationId, "target_ipv6", "::2");
    insertSignature(expectationId, "target_hostname", "host-a");
    insertSignature(expectationId, "parent_process_name", "implant.exe");

    runMigration();

    assertThat(signaturesOf(expectationId))
        .extracting(row -> row[0] + "=" + row[1])
        .containsExactly(
            "end_date=2026-07-31T11:00:00Z",
            "parent_process_name=implant.exe",
            "source_ipv4_address=10.0.0.1",
            "source_ipv6_address=::1",
            "start_date=2026-07-31T10:00:00Z",
            "target_hostname_address=host-a",
            "target_ipv4_address=10.0.0.2",
            "target_ipv6_address=::2");
  }

  @Test
  @DisplayName("Drops a legacy row whose canonical twin already exists instead of violating the PK")
  void legacy_duplicates_are_dropped() {
    String expectationId = persistExpectation();
    insertSignature(expectationId, "source_ipv4", "10.0.0.1");
    insertSignature(expectationId, "source_ipv4_address", "10.0.0.1");

    runMigration();

    assertThat(signaturesOf(expectationId))
        .extracting(row -> row[0] + "=" + row[1])
        .containsExactly("source_ipv4_address=10.0.0.1");
  }

  @Test
  @DisplayName("Re-running the migration is a no-op (idempotent)")
  void migration_is_idempotent() {
    String expectationId = persistExpectation();
    insertSignature(expectationId, "start_time", "2026-07-31T10:00:00Z");

    runMigration();
    assertThatCode(this::runMigration).doesNotThrowAnyException();

    assertThat(signaturesOf(expectationId))
        .extracting(row -> row[0] + "=" + row[1])
        .containsExactly("start_date=2026-07-31T10:00:00Z");
  }
}
