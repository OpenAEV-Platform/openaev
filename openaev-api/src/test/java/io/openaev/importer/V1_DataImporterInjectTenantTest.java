package io.openaev.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Tenant of an imported inject")
class V1_DataImporterInjectTenantTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Nested
  @DisplayName("Inject with a parent")
  class WithParent {

    @Test
    @DisplayName("given_simulationInTenantA_should_takeTenantA")
    void given_simulationInTenantA_should_takeTenantA() {
      // Arrange
      Exercise exercise = exerciseIn(TENANT_A);

      // Act
      String tenant = V1_DataImporter.injectTenant(exercise, null, TENANT_A);

      // Assert
      assertThat(tenant).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("given_scenarioInTenantA_should_takeTenantA")
    void given_scenarioInTenantA_should_takeTenantA() {
      // Arrange
      Scenario scenario = scenarioIn(TENANT_A);

      // Act
      String tenant = V1_DataImporter.injectTenant(null, scenario, TENANT_A);

      // Assert
      assertThat(tenant).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("given_parentInTenantA_and_writeTenantB_should_refuseTheWrite")
    void given_parentInTenantA_and_writeTenantB_should_refuseTheWrite() {
      // Arrange
      Exercise exercise = exerciseIn(TENANT_A);

      // Act / Assert
      assertThatThrownBy(() -> V1_DataImporter.injectTenant(exercise, null, TENANT_B))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("cross-tenant write: Inject carries a tenant other than its parent's");
    }
  }

  @Nested
  @DisplayName("Atomic testing, no parent")
  class WithoutParent {

    @Test
    @DisplayName("given_writeTenantB_should_takeTenantB")
    void given_writeTenantB_should_takeTenantB() {
      // Act
      String tenant = V1_DataImporter.injectTenant(null, null, TENANT_B);

      // Assert
      assertThat(tenant).isEqualTo(TENANT_B);
    }

    @Test
    @DisplayName("given_noWriteTenant_should_refuseTheWrite")
    void given_noWriteTenant_should_refuseTheWrite() {
      // Act / Assert
      assertThatThrownBy(() -> V1_DataImporter.injectTenant(null, null, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("unattributed tenant write: Inject has no parent to take a tenant from");
    }
  }

  private static Exercise exerciseIn(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    return exercise;
  }

  private static Scenario scenarioIn(String tenantId) {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantId));
    return scenario;
  }
}
