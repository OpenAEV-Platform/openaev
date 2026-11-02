package io.openaev.importer;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three native inserts the importer uses for injects write the tenant they are given, not the
 * ambient one. The parent lives in tenant A and the thread-local is set to a third tenant C, so
 * neither the thread-local nor the default-tenant fallback can produce the expected row.
 *
 * <p>No injector contract is set: the composite foreign key on {@code (inject_injector_contract,
 * tenant_id)} would otherwise refuse a mismatched tenant before the value could be observed, while
 * {@code inject_exercise} and {@code inject_scenario} are single-column keys that accept it.
 *
 * <p>The row tenant is read with raw JDBC on the test connection so the statement inspector never
 * rewrites the ground-truth read.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injects")
@WithMockUser(isAdmin = true)
@DisplayName("Inject import inserts write the tenant of the parent, not the ambient one")
class InjectImportSaveTenantTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String tenantC;

  @BeforeEach
  void seedTheParentTenantAndAThirdAmbientTenant() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    tenantA = seedTenant("inject-import-save-a");
    tenantC = seedTenant("inject-import-save-c");
    DefaultTenantScopeTestListener.setAmbientScope(entityManager, tenantA + "," + tenantC);
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Inject of a simulation")
  class ForExercise {

    @Test
    @DisplayName("given_parentInTenantA_and_ambientTenantC_should_writeInjectInTenantA")
    void given_parentInTenantA_and_ambientTenantC_should_writeInjectInTenantA() {
      // Arrange
      TenantContext.setCurrentTenant(tenantA);
      Exercise exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
      TenantContext.setCurrentTenant(tenantC);
      String injectId = UUID.randomUUID().toString();

      // Act
      injectRepository.importSaveForExercise(
          injectId,
          "imported",
          null,
          null,
          null,
          null,
          false,
          true,
          exercise.getId(),
          0L,
          null,
          exercise.getTenant().getId());

      // Assert
      assertThat(injectTenant(injectId)).isEqualTo(tenantA).isNotEqualTo(tenantC);
    }
  }

  @Nested
  @DisplayName("Inject of a scenario")
  class ForScenario {

    @Test
    @DisplayName("given_parentInTenantA_and_ambientTenantC_should_writeInjectInTenantA")
    void given_parentInTenantA_and_ambientTenantC_should_writeInjectInTenantA() {
      // Arrange
      TenantContext.setCurrentTenant(tenantA);
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      TenantContext.setCurrentTenant(tenantC);
      String injectId = UUID.randomUUID().toString();

      // Act
      injectRepository.importSaveForScenario(
          injectId,
          "imported",
          null,
          null,
          null,
          null,
          false,
          true,
          scenario.getId(),
          0L,
          null,
          scenario.getTenant().getId());

      // Assert
      assertThat(injectTenant(injectId)).isEqualTo(tenantA).isNotEqualTo(tenantC);
    }
  }

  @Nested
  @DisplayName("Atomic testing, no parent")
  class StandAlone {

    @Test
    @DisplayName("given_writeTenantA_and_ambientTenantC_should_writeInjectInTenantA")
    void given_writeTenantA_and_ambientTenantC_should_writeInjectInTenantA() {
      // Arrange
      TenantContext.setCurrentTenant(tenantC);
      String injectId = UUID.randomUUID().toString();

      // Act
      injectRepository.importSaveStandAlone(
          injectId, "imported", null, null, null, null, false, true, 0L, null, tenantA);

      // Assert
      assertThat(injectTenant(injectId)).isEqualTo(tenantA).isNotEqualTo(tenantC);
    }
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
                + " VALUES (:id, :name, now(), now())")
        .setParameter("id", id)
        .setParameter("name", name + "-" + id)
        .executeUpdate();
    return id;
  }

  private String injectTenant(String injectId) {
    entityManager.flush();
    List<String> tenants =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  List<String> values = new ArrayList<>();
                  try (PreparedStatement statement =
                      connection.prepareStatement(
                          "SELECT tenant_id FROM injects WHERE inject_id = ?")) {
                    statement.setString(1, injectId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                      while (resultSet.next()) {
                        values.add(resultSet.getString(1));
                      }
                    }
                  }
                  return values;
                });
    assertThat(tenants).hasSize(1);
    return tenants.getFirst();
  }
}
