package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20260719200000000__Add_attack_path_tables;
import io.openaev.utils.mockUser.WithMockUser;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the attack path POC migration is applied, additive, and idempotent: the three tables and
 * their indexes exist, {@code tenant_id} is NOT NULL with a FK to {@code tenants}, and re-running
 * the migration is a no-op.
 *
 * <p>{@code @Transactional} so the idempotency test's re-run of the migration rolls back with the
 * test transaction instead of leaking any DDL side effect into the other tests.
 */
@Transactional
@WithMockUser(isAdmin = true)
class AttackPathSchemaMigrationTest extends IntegrationTest {

  @Autowired private V6_20260719200000000__Add_attack_path_tables migration;

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

  private long foreignKeyCount(String constraint) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_name = :c AND constraint_type = 'FOREIGN KEY'")
                .setParameter("c", constraint)
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

  @Test
  @DisplayName("The three attackpath_* tables exist")
  void tables_exist() {
    assertThat(tableCount("attackpath_execution")).isEqualTo(1);
    assertThat(tableCount("attackpath_finding")).isEqualTo(1);
    assertThat(tableCount("attackpath_execution_finding")).isEqualTo(1);
  }

  @Test
  @DisplayName("The indexes lead with simulation_id")
  void indexes_exist() {
    // No single-column idx_ap_exec_sim: the composite below leads with simulation_id and covers the
    // graph read too, so the single-column index would be redundant.
    assertThat(indexCount("idx_ap_exec_sim")).isZero();
    assertThat(indexCount("idx_ap_exec_sim_targetkey")).isEqualTo(1);
    // The (inject_id, agent_id) lookup index #204/#202 query by to find a run's rows. Asserted so a
    // future migration edit cannot drop it unnoticed.
    assertThat(indexCount("idx_ap_exec_inject_agent")).isEqualTo(1);
    assertThat(indexCount("idx_ap_find_sim")).isEqualTo(1);
    assertThat(indexCount("idx_ap_find_sim_endpointkey_type")).isEqualTo(1);
    assertThat(indexCount("idx_ap_ef_finding")).isEqualTo(1);
  }

  @Test
  @DisplayName("The tenant FK columns are indexed (so ON DELETE CASCADE does not seq-scan)")
  void tenant_fk_columns_are_indexed() {
    assertThat(indexCount("idx_ap_exec_tenant")).isEqualTo(1);
    assertThat(indexCount("idx_ap_find_tenant")).isEqualTo(1);
  }

  @Test
  @DisplayName("tenant_id is NOT NULL with a FK to tenants")
  void tenant_column_is_scoped() {
    assertThat(isNullable("attackpath_execution", "tenant_id")).isEqualTo("NO");
    assertThat(isNullable("attackpath_finding", "tenant_id")).isEqualTo("NO");
    assertThat(foreignKeyCount("attackpath_execution_tenant_fk")).isEqualTo(1);
    assertThat(foreignKeyCount("attackpath_finding_tenant_fk")).isEqualTo(1);
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
