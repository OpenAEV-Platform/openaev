package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261014170000000__Add_tenant_id_to_chaining_tables.TENANT_TABLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies, on the live schema, that every chaining table carries its own {@code tenant_id}, and
 * that the backfill attributes each row to the tenant of the workflow's owner, whichever of the
 * simulation or the scenario owns it.
 *
 * <p>{@code @Transactional} so the DDL the backfill tests apply (dropping {@code NOT NULL} to seed
 * unattributed rows) rolls back with the test transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261014170000000: tenant_id on the chaining tables")
class AddTenantIdToChainingTablesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261014170000000__Add_tenant_id_to_chaining_tables migration;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  @BeforeEach
  void resetComposers() {
    exerciseComposer.reset();
    scenarioComposer.reset();
  }

  @Nested
  @DisplayName("Schema")
  class Schema {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.migration.AddTenantIdToChainingTablesMigrationTest#tenantTables")
    @DisplayName("The table has a NOT NULL tenant_id with no default")
    void given_migratedSchema_should_haveNotNullTenantIdWithoutDefault(String table) {
      // Act
      List<Object[]> columns =
          nativeRows(
              "SELECT is_nullable, column_default FROM information_schema.columns"
                  + " WHERE table_schema = current_schema() AND table_name = '"
                  + table
                  + "' AND column_name = 'tenant_id'");

      // Assert
      assertThat(columns).as("tenant_id on %s", table).hasSize(1);
      assertThat(columns.getFirst()[0]).isEqualTo("NO");
      assertThat(columns.getFirst()[1]).as("default of %s.tenant_id", table).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.migration.AddTenantIdToChainingTablesMigrationTest#tenantTables")
    @DisplayName("The table references tenants with ON DELETE CASCADE")
    void given_migratedSchema_should_referenceTenantsWithCascade(String table) {
      // Act
      long foreignKeys =
          nativeCount(
              "SELECT count(*) FROM pg_constraint c"
                  + " JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]"
                  + " WHERE c.contype = 'f' AND c.conrelid = '"
                  + table
                  + "'::regclass AND c.confrelid = 'tenants'::regclass"
                  + " AND array_length(c.conkey, 1) = 1 AND a.attname = 'tenant_id'"
                  + " AND c.confdeltype = 'c'");

      // Assert
      assertThat(foreignKeys).as("foreign key %s.tenant_id -> tenants", table).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.migration.AddTenantIdToChainingTablesMigrationTest#tenantTables")
    @DisplayName("The table has an index led by tenant_id")
    void given_migratedSchema_should_indexTenantId(String table) {
      // Act
      long indexes =
          nativeCount(
              "SELECT count(*) FROM pg_index i"
                  + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]"
                  + " WHERE i.indrelid = '"
                  + table
                  + "'::regclass AND a.attname = 'tenant_id'");

      // Assert
      assertThat(indexes).as("index on %s.tenant_id", table).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"conditions_steps", "workflow_state_entries"})
    @DisplayName("A table only ever reached through its parent gets no tenant_id")
    void given_migratedSchema_should_leaveParentKeyedTablesWithoutTenantId(String table) {
      // Act
      long columns =
          nativeCount(
              "SELECT count(*) FROM information_schema.columns"
                  + " WHERE table_schema = current_schema() AND table_name = '"
                  + table
                  + "' AND column_name = 'tenant_id'");

      // Assert
      assertThat(columns).isZero();
    }
  }

  @Nested
  @DisplayName("Backfill")
  class Backfill {

    private Tenant simulationTenant;
    private Tenant scenarioTenant;
    private Owner simulationOwner;
    private Owner scenarioOwner;

    @BeforeEach
    void seedUnattributedRows() {
      simulationTenant = persistTenant("chaining-simulation");
      scenarioTenant = persistTenant("chaining-scenario");
      Exercise simulation = ExerciseFixture.createDefaultExercise();
      simulation.setTenant(simulationTenant);
      exerciseComposer.forExercise(simulation).persist();
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTenant(scenarioTenant);
      scenarioComposer.forScenario(scenario).persist();
      entityManager.flush();

      TENANT_TABLES.forEach(
          table -> execute("ALTER TABLE " + table + " ALTER COLUMN tenant_id DROP NOT NULL"));
      simulationOwner = seedChainingRows("workflow_simulation_id", simulation.getId());
      scenarioOwner = seedChainingRows("workflow_scenario_id", scenario.getId());
    }

    @Test
    @DisplayName("Rows of a simulation-backed workflow get the simulation's tenant")
    void given_simulationBackedWorkflow_should_attributeEveryRowToTheSimulationTenant() {
      // Act
      runMigration();

      // Assert
      assertOwnerRowsIn(simulationOwner, simulationTenant.getId());
    }

    @Test
    @DisplayName("Rows of a scenario-backed workflow get the scenario's tenant")
    void given_scenarioBackedWorkflow_should_attributeEveryRowToTheScenarioTenant() {
      // Act
      runMigration();

      // Assert
      assertOwnerRowsIn(scenarioOwner, scenarioTenant.getId());
    }

    @Test
    @DisplayName("A row with no parent at all falls back to the default tenant")
    void given_orphanRows_should_attributeThemToTheDefaultTenant() {
      // Arrange
      String orphanRule = id();
      execute(
          "INSERT INTO workflow_scope_rules (workflow_scope_rule_id) VALUES ('"
              + orphanRule
              + "')");
      String orphanQueueEntry = id();
      execute(
          "INSERT INTO steps_delay_queue (steps_delay_queue_id) VALUES ('"
              + orphanQueueEntry
              + "')");
      String orphanCondition = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type) VALUES ('"
              + orphanCondition
              + "', 'AND')");

      // Act
      runMigration();

      // Assert
      assertThat(tenantOf("workflow_scope_rules", "workflow_scope_rule_id", orphanRule))
          .isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(tenantOf("steps_delay_queue", "steps_delay_queue_id", orphanQueueEntry))
          .isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(tenantOf("conditions", "condition_id", orphanCondition))
          .isEqualTo(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("The column is NOT NULL again once the backfill is done")
    void given_backfilledRows_should_restoreNotNull() {
      // Act
      runMigration();

      // Assert
      for (String table : TENANT_TABLES) {
        assertThat(
                nativeRows(
                        "SELECT is_nullable FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = '"
                            + table
                            + "' AND column_name = 'tenant_id'")
                    .getFirst()[0])
            .as("%s.tenant_id", table)
            .isEqualTo("NO");
      }
    }

    private void assertOwnerRowsIn(Owner owner, String tenantId) {
      assertThat(tenantOf("workflows", "workflow_id", owner.workflow())).isEqualTo(tenantId);
      assertThat(tenantOf("steps", "step_id", owner.step())).isEqualTo(tenantId);
      for (String condition : owner.conditions()) {
        assertThat(tenantOf("conditions", "condition_id", condition))
            .as("condition %s", condition)
            .isEqualTo(tenantId);
      }
      assertThat(tenantOf("steps_delay_queue", "steps_delay_queue_id", owner.queueEntry()))
          .isEqualTo(tenantId);
      assertThat(tenantOf("workflow_scope_rules", "workflow_scope_rule_id", owner.scopeRule()))
          .isEqualTo(tenantId);
      assertThat(tenantOf("scope_variables", "scope_variable_id", owner.scopeVariable()))
          .isEqualTo(tenantId);
      for (String state : owner.states()) {
        assertThat(tenantOf("workflow_states", "workflow_state_id", state))
            .as("state %s", state)
            .isEqualTo(tenantId);
      }
    }

    /**
     * One workflow of the given owner and a row in each child table, with every path the backfill
     * has to follow for a condition: its workflow, a step link, the step it comes from, and a
     * parent two levels up.
     */
    private Owner seedChainingRows(String ownerColumn, String ownerId) {
      String workflow = id();
      execute(
          "INSERT INTO workflows (workflow_id, workflow_status, workflow_version, "
              + ownerColumn
              + ") VALUES ('"
              + workflow
              + "', 'TEMPLATE', 0, '"
              + ownerId
              + "')");
      String step = id();
      execute(
          "INSERT INTO steps (step_id, step_action_class, step_limit_execution, step_status,"
              + " step_workflow_id) VALUES ('"
              + step
              + "', 'INJECT_EXECUTION', 1, 'TEMPLATE', '"
              + workflow
              + "')");

      String byWorkflow = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type, condition_workflow_id) VALUES ('"
              + byWorkflow
              + "', 'AND', '"
              + workflow
              + "')");
      String byStepLink = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type) VALUES ('"
              + byStepLink
              + "', 'AND')");
      execute(
          "INSERT INTO conditions_steps (condition_step_id, condition_id, step_id, is_root)"
              + " VALUES ('"
              + id()
              + "', '"
              + byStepLink
              + "', '"
              + step
              + "', true)");
      String byStepFrom = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type, step_from_id) VALUES ('"
              + byStepFrom
              + "', 'EQ', '"
              + step
              + "')");
      String child = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type, condition_parent_id) VALUES ('"
              + child
              + "', 'OR', '"
              + byStepLink
              + "')");
      String grandChild = id();
      execute(
          "INSERT INTO conditions (condition_id, condition_type, condition_parent_id) VALUES ('"
              + grandChild
              + "', 'EQ', '"
              + child
              + "')");

      String queueEntry = id();
      execute(
          "INSERT INTO steps_delay_queue (steps_delay_queue_id, steps_delay_queue_step_template_id,"
              + " steps_delay_queue_workflow_run_id) VALUES ('"
              + queueEntry
              + "', '"
              + step
              + "', '"
              + workflow
              + "')");
      String scopeRule = id();
      execute(
          "INSERT INTO workflow_scope_rules (workflow_scope_rule_id, workflow_id) VALUES ('"
              + scopeRule
              + "', '"
              + workflow
              + "')");
      String scopeVariable = id();
      execute(
          "INSERT INTO scope_variables (scope_variable_id, scope_variable_key, scope_variable_type,"
              + " scope_variable_value, scope_variable_workflow) VALUES ('"
              + scopeVariable
              + "', 'key', 'Text', 'value', '"
              + workflow
              + "')");
      String globalState = id();
      execute(
          "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id) VALUES ('"
              + globalState
              + "', '"
              + workflow
              + "')");
      String localState = id();
      execute(
          "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id,"
              + " workflow_step_template_id) VALUES ('"
              + localState
              + "', '"
              + workflow
              + "', '"
              + step
              + "')");

      return new Owner(
          workflow,
          step,
          List.of(byWorkflow, byStepLink, byStepFrom, child, grandChild),
          queueEntry,
          scopeRule,
          scopeVariable,
          List.of(globalState, localState));
    }

    private Tenant persistTenant(String name) {
      return tenantRepository.save(
          TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8)));
    }
  }

  @Nested
  @DisplayName("Idempotency")
  class Idempotency {

    @Test
    @DisplayName("Re-running the migration is a no-op")
    void given_alreadyAppliedMigration_should_rerunWithoutError() {
      // Act + Assert
      assertThatCode(AddTenantIdToChainingTablesMigrationTest.this::runMigration)
          .doesNotThrowAnyException();
    }
  }

  // -- helpers --

  static List<String> tenantTables() {
    return TENANT_TABLES;
  }

  private record Owner(
      String workflow,
      String step,
      List<String> conditions,
      String queueEntry,
      String scopeRule,
      String scopeVariable,
      List<String> states) {}

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private void runMigration() {
    entityManager.flush();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(contextOn(connection));
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private void execute(String sql) {
    entityManager.createNativeQuery(sql).executeUpdate();
  }

  private String tenantOf(String table, String idColumn, String id) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = '" + id + "'")
            .getSingleResult();
  }

  private long nativeCount(String sql) {
    return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
  }

  @SuppressWarnings("unchecked")
  private List<Object[]> nativeRows(String sql) {
    List<?> rows = entityManager.createNativeQuery(sql).getResultList();
    return rows.stream()
        .map(row -> row instanceof Object[] array ? array : new Object[] {row})
        .toList();
  }

  private static Context contextOn(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }
}
