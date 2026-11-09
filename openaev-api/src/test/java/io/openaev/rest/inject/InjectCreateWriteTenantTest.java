package io.openaev.rest.inject;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An inject created under a scenario or a simulation takes that parent's tenant. {@code injects}
 * carries the composite foreign key {@code (inject_injector_contract, tenant_id)} onto {@code
 * injectors_contracts}, and the contract is resolved in the parent's tenant, so an inject stamped
 * with any other tenant is refused by the database. {@code InjectService} stamps the inject with
 * its parent's tenant through {@code InjectUtils#attachToScenario} and {@code
 * InjectUtils#attachToExercise}, so {@code TenantBaseListener} finds the tenant set and keeps it.
 * The ambient tenant plays no part: the non-prefixed route never sets it ({@code TenantInterceptor}
 * arms it on the tenant-prefixed route only), so a caller who belongs to several tenants reaches
 * the create with the platform default as the ambient tenant.
 *
 * <p>The service is called directly, with a scope spanning the default tenant and the parent's: the
 * scenario and simulation inject endpoints do not find a parent of another tenant on the
 * non-prefixed route while those two tables are still v1, so the shape is pinned where the stamp is
 * decided rather than behind a route that refuses it first.
 *
 * <p>Deliberately NOT {@code @Transactional}: the rows are committed and read back with plain JDBC
 * from outside the persistence context.
 */
@DisplayName("Creating an inject writes it in its parent's tenant, not the ambient one")
@WithMockUser(isAdmin = true)
class InjectCreateWriteTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private InjectService injectService;
  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private DataSource dataSource;

  private String otherTenantId;

  @BeforeEach
  void setUp() throws Exception {
    // The built-in contract exists in both tenants, as it does in production: the contract id
    // repeats across tenants (#8485), which is what makes the inject's own tenant matter to the
    // composite foreign key.
    otherTenantId = tenantHelper.createTenantWithCurrentUser("inject-create-write-tenant").getId();
    builtinConnectorRegistration.register(emailInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        emailInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  @Nested
  @DisplayName("Under a scenario of another tenant")
  class ScenarioParent {

    @Test
    @DisplayName("given_noAmbientTenant_should_writeTheInjectInTheScenarioTenant")
    void given_noAmbientTenant_should_writeTheInjectInTheScenarioTenant() {
      // -- ARRANGE --
      String scenarioId = saveScenarioIn(otherTenantId);
      InjectInput input = injectInput();
      withNoAmbientTenant();

      // -- ACT --
      tenantTx.execute(
          bothTenantsScope(),
          () -> {
            Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
            injectService.createAndSaveInject(bothTenantsScope(), null, scenario, input);
          });

      // -- ASSERT --
      assertThat(injectTenants(input.getTitle())).containsExactly(otherTenantId);
    }

    @Test
    @DisplayName("given_noAmbientTenant_should_writeEveryInjectOfABulkInTheScenarioTenant")
    void given_noAmbientTenant_should_writeEveryInjectOfABulkInTheScenarioTenant() {
      // -- ARRANGE --
      String scenarioId = saveScenarioIn(otherTenantId);
      InjectInput first = injectInput();
      InjectInput second = injectInput();
      withNoAmbientTenant();

      // -- ACT --
      tenantTx.execute(
          bothTenantsScope(),
          () -> {
            Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
            injectService.createAndSaveInjectList(
                bothTenantsScope(), null, scenario, List.of(first, second));
          });

      // -- ASSERT --
      assertThat(injectTenants(first.getTitle())).containsExactly(otherTenantId);
      assertThat(injectTenants(second.getTitle())).containsExactly(otherTenantId);
    }
  }

  @Nested
  @DisplayName("Under a simulation of another tenant")
  class SimulationParent {

    @Test
    @DisplayName("given_noAmbientTenant_should_writeTheInjectInTheSimulationTenant")
    void given_noAmbientTenant_should_writeTheInjectInTheSimulationTenant() {
      // -- ARRANGE --
      String exerciseId = saveExerciseIn(otherTenantId);
      InjectInput input = injectInput();
      withNoAmbientTenant();

      // -- ACT --
      tenantTx.execute(
          bothTenantsScope(),
          () -> {
            Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
            injectService.createAndSaveInject(bothTenantsScope(), exercise, null, input);
          });

      // -- ASSERT --
      assertThat(injectTenants(input.getTitle())).containsExactly(otherTenantId);
    }
  }

  @Nested
  @DisplayName("Copying the injects of a scenario of another tenant into a simulation")
  class ScenarioLaunch {

    @Test
    @DisplayName("given_noAmbientTenant_should_writeTheSimulationInjectsInTheScenarioTenant")
    void given_noAmbientTenant_should_writeTheSimulationInjectsInTheScenarioTenant() {
      // -- ARRANGE --
      String scenarioId = saveScenarioIn(otherTenantId);
      InjectInput input = injectInput();
      tenantTx.execute(
          bothTenantsScope(),
          () -> {
            Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
            injectService.createAndSaveInject(bothTenantsScope(), null, scenario, input);
          });
      withNoAmbientTenant();

      // -- ACT --
      String simulationId =
          tenantTx.execute(
              bothTenantsScope(),
              () -> {
                Scenario scenario = scenarioRepository.findById(scenarioId).orElseThrow();
                return scenarioToExerciseService.toExercise(scenario, null, false, null).getId();
              });

      // -- ASSERT --
      assertThat(simulationInjectTenants(simulationId)).containsExactly(otherTenantId);
    }
  }

  // -- Helpers --

  private TxCtx bothTenantsScope() {
    return TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, otherTenantId));
  }

  /** The request thread's ambient tenant is empty, as a fresh one is: it falls back to default. */
  private static void withNoAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private String saveScenarioIn(String tenantId) {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantId));
    return scenarioRepository.save(scenario).getId();
  }

  private String saveExerciseIn(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    return exerciseRepository.save(exercise).getId();
  }

  private static InjectInput injectInput() {
    InjectInput input = new InjectInput();
    input.setTitle("inject-create-write-tenant-" + UUID.randomUUID());
    input.setInjectorContract(EMAIL_DEFAULT);
    input.setDependsDuration(0L);
    return input;
  }

  /** Read with plain JDBC, outside the persistence context and the statement inspector. */
  private List<String> injectTenants(String title) {
    return new JdbcTemplate(dataSource)
        .queryForList("SELECT tenant_id FROM injects WHERE inject_title = ?", String.class, title);
  }

  private List<String> simulationInjectTenants(String simulationId) {
    return new JdbcTemplate(dataSource)
        .queryForList(
            "SELECT tenant_id FROM injects WHERE inject_exercise = ?", String.class, simulationId);
  }
}
