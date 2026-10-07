package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ADR-011 normalized WorkflowState store migration is applied, additive and
 * idempotent: the {@code workflow_state_entries} table exists with the expected types/constraints,
 * and re-running the migration is a no-op. (The {@code workflows.storage_mode} column it also added
 * is dropped by {@code V6_20261005160000000}, see {@link
 * MigrateWorkflowStateToNormalizedEntriesMigrationTest}.)
 *
 * <p>{@code @Transactional} so the idempotency test's re-run of the migration rolls back with the
 * test transaction instead of leaking any DDL side effect into the other tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261005105200000 — normalized workflow state tables")
class AddWorkflowStateNormalizedTablesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261005105200000__Add_workflow_state_normalized_tables migration;

  private long tableCount(String table) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = :t")
                .setParameter("t", table)
                .getSingleResult())
        .longValue();
  }

  private long indexCount(String index) {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM pg_indexes WHERE indexname = :i")
                .setParameter("i", index)
                .getSingleResult())
        .longValue();
  }

  private long constraintCount(String constraint, String type) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_name = :c AND constraint_type = :ty")
                .setParameter("c", constraint)
                .setParameter("ty", type)
                .getSingleResult())
        .longValue();
  }

  private String isNullable(String table, String column) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = :t AND column_name = :c")
            .setParameter("t", table)
            .setParameter("c", column)
            .getSingleResult();
  }

  private String dataType(String table, String column) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_name = :t AND column_name = :c")
            .setParameter("t", table)
            .setParameter("c", column)
            .getSingleResult();
  }

  @Nested
  @DisplayName("schema")
  class Schema {

    @Test
    @DisplayName("the normalized WorkflowState entries table exists")
    void given_migratedSchema_should_haveTheEntriesTable() {
      // Act + Assert — the migration ran at startup
      assertThat(tableCount("workflow_state_entries")).isEqualTo(1);
    }

    @Test
    @DisplayName("workflow_state_entries has the expected columns, types and constraints")
    void given_entriesTable_should_haveTheExpectedColumnsAndConstraints() {
      // Act + Assert
      assertThat(isNullable("workflow_state_entries", "workflow_state_id")).isEqualTo("NO");
      assertThat(isNullable("workflow_state_entries", "entry_type")).isEqualTo("NO");
      assertThat(isNullable("workflow_state_entries", "entry_key")).isEqualTo("NO");
      assertThat(isNullable("workflow_state_entries", "entry_value")).isEqualTo("NO");
      // correlation_hash / correlation_type are optional (only CORRELATED rows carry them).
      assertThat(isNullable("workflow_state_entries", "correlation_hash")).isEqualTo("YES");
      assertThat(isNullable("workflow_state_entries", "correlation_type")).isEqualTo("YES");
      assertThat(dataType("workflow_state_entries", "workflow_state_id"))
          .isEqualTo("character varying");
      assertThat(dataType("workflow_state_entries", "correlation_hash"))
          .isEqualTo("character varying");
      assertThat(dataType("workflow_state_entries", "entry_value")).isEqualTo("text");
      assertThat(dataType("workflow_state_entries", "created_at"))
          .isEqualTo("timestamp with time zone");

      assertThat(constraintCount("chk_wse_entry_type", "CHECK")).isEqualTo(1);
      assertThat(indexCount("idx_wse_lookup")).isEqualTo(1);
    }

    @Test
    @DisplayName("workflow_state_entries has one partial unique index per entry type")
    void given_entriesTable_should_haveOnePartialUniqueIndexPerEntryType() {
      // Act + Assert
      assertThat(indexCount("uq_wse_input")).isEqualTo(1);
      assertThat(indexCount("uq_wse_hash")).isEqualTo(1);
      assertThat(indexCount("uq_wse_correlated")).isEqualTo(1);
      assertThat(tableCount("workflow_state_correlation_progress")).isZero();
    }
  }

  @Nested
  @DisplayName("idempotency")
  class Idempotency {

    @Test
    @DisplayName("re-running the migration is a no-op")
    void given_alreadyAppliedMigration_should_rerunWithoutError() {
      // Act + Assert
      assertThatCode(
              () ->
                  entityManager
                      .unwrap(Session.class)
                      .doWork(
                          connection ->
                              runMigration(
                                  new Context() {
                                    @Override
                                    public Configuration getConfiguration() {
                                      return null;
                                    }

                                    @Override
                                    public java.sql.Connection getConnection() {
                                      return connection;
                                    }
                                  })))
          .doesNotThrowAnyException();
    }
  }

  private void runMigration(Context context) {
    try {
      migration.migrate(context);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
