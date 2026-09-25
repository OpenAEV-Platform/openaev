package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ADR-007 normalized WorkflowState store migration is applied, additive and
 * idempotent: the two tables ({@code workflow_state_entries}, {@code
 * workflow_state_correlation_progress}) and the {@code workflows.storage_mode} column exist with
 * the expected types/constraints, and re-running the migration is a no-op.
 *
 * <p>{@code @Transactional} so the idempotency test's re-run of the migration rolls back with the
 * test transaction instead of leaking any DDL side effect into the other tests.
 */
@Transactional
@WithMockUser(isAdmin = true)
class AddWorkflowStateNormalizedTablesMigrationTest extends IntegrationTest {

  @Autowired private V6_20260908080000000__Add_workflow_state_normalized_tables migration;

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

  @Test
  @DisplayName("The two normalized WorkflowState tables exist")
  void tables_exist() {
    assertThat(tableCount("workflow_state_entries")).isEqualTo(1);
    assertThat(tableCount("workflow_state_correlation_progress")).isEqualTo(1);
  }

  @Test
  @DisplayName("workflow_state_entries has the expected columns, types and constraints")
  void entries_columns_and_constraints() {
    assertThat(isNullable("workflow_state_entries", "workflow_state_id")).isEqualTo("NO");
    assertThat(isNullable("workflow_state_entries", "entry_type")).isEqualTo("NO");
    assertThat(isNullable("workflow_state_entries", "entry_key")).isEqualTo("NO");
    assertThat(isNullable("workflow_state_entries", "entry_value")).isEqualTo("NO");
    // correlation_hash is optional (only CORRELATED rows carry it).
    assertThat(isNullable("workflow_state_entries", "correlation_hash")).isEqualTo("YES");
    assertThat(dataType("workflow_state_entries", "correlation_hash")).isEqualTo("bytea");
    assertThat(dataType("workflow_state_entries", "entry_value")).isEqualTo("text");
    assertThat(dataType("workflow_state_entries", "created_at"))
        .isEqualTo("timestamp with time zone");

    assertThat(constraintCount("uq_wse_state_type_key_value", "UNIQUE")).isEqualTo(1);
    assertThat(indexCount("idx_wse_lookup")).isEqualTo(1);
    assertThat(indexCount("idx_wse_correlation_hash")).isEqualTo(1);
  }

  @Test
  @DisplayName("workflow_state_correlation_progress is keyed by correlation_hash (BYTEA)")
  void correlation_progress_columns_and_constraints() {
    assertThat(dataType("workflow_state_correlation_progress", "correlation_hash"))
        .isEqualTo("bytea");
    assertThat(isNullable("workflow_state_correlation_progress", "correlation_hash"))
        .isEqualTo("NO");
    assertThat(isNullable("workflow_state_correlation_progress", "workflow_state_id"))
        .isEqualTo("NO");
    assertThat(dataType("workflow_state_correlation_progress", "keys_present")).isEqualTo("ARRAY");
    assertThat(isNullable("workflow_state_correlation_progress", "is_complete")).isEqualTo("NO");

    assertThat(constraintCount("workflow_state_correlation_progress_pkey", "PRIMARY KEY"))
        .isEqualTo(1);
    assertThat(indexCount("idx_wscp_ready")).isEqualTo(1);
  }

  @Test
  @DisplayName("workflows.storage_mode exists, NOT NULL, defaults to LEGACY_JSONB")
  void storage_mode_column_exists() {
    assertThat(isNullable("workflows", "storage_mode")).isEqualTo("NO");
    assertThat(dataType("workflows", "storage_mode")).isEqualTo("character varying");
    String columnDefault =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_name = 'workflows' AND column_name = 'storage_mode'")
                .getSingleResult();
    assertThat(columnDefault).contains("LEGACY_JSONB");
  }

  @Test
  @DisplayName("Re-running the migration is a no-op (idempotent)")
  void migration_is_idempotent() {
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

  private void runMigration(Context context) {
    try {
      migration.migrate(context);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
