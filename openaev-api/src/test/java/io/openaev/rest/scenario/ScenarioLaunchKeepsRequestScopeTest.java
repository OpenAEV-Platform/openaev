package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Launching a simulation from a scenario leaves the request transaction with the caller's own
 * tenant scope and marking clearance. The copy of the scenario's injects runs in the scenario's
 * tenant at system clearance; once it is done, whatever the request reads or writes next is decided
 * by the caller's scope again, on the path-prefixed route and on the {@code X-Tenant-Ids} route.
 *
 * <p>The caller is not an administrator and holds no marking grant, so its clearance is empty while
 * the scenario's tenant carries a marking definition: the two clearances differ, which makes the
 * marking dimension observable. The class runs with the production list of active tables. {@link
 * LaunchScopeProbe} reads both settings of the request transaction right before and right after the
 * launch, inside that same transaction.
 */
@Transactional
@Import(ScenarioLaunchKeepsRequestScopeTest.LaunchScopeProbe.class)
@WithMockUser(isAdmin = false)
@DisplayName("Launching a simulation from a scenario keeps the request's scope")
class ScenarioLaunchKeepsRequestScopeTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String RUNNING_EXERCISE = "/{scenarioId}/exercise/running";
  private static final Set<Capability> LAUNCH =
      Set.of(Capability.ACCESS_ASSESSMENT, Capability.LAUNCH_ASSESSMENT);

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private LaunchScopeProbe probe;

  @BeforeEach
  void reset() {
    scenarioComposer.reset();
    probe.reset();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("on the tenant-prefixed route")
  class OnThePrefixedRoute {

    @Test
    @DisplayName("given a launch, should leave the request's tenant scope and clearance in place")
    void given_launchOnTheTenantPath_should_keepTheRequestScopeAfterTheLaunch() throws Exception {
      // -- ARRANGE --
      String tenantA = tenantHelper.createTenantWithCapabilities("launch-scope-a", LAUNCH).getId();
      seedMarking(tenantA);
      String scenarioId = seedScenario(tenantA);
      TenantContext.clearCurrentTenant();

      // -- ACT --
      mvc.perform(post(TENANT_SCENARIO_URI + RUNNING_EXERCISE, tenantA, scenarioId).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertThat(probe.before()).isNotNull();
      assertThat(probe.before().tenants()).containsExactly(tenantA);
      assertThat(probe.before().markings())
          .as("the caller holds no marking grant, so its clearance is empty")
          .isEmpty();
      assertThat(probe.after())
          .as("the request continues with the scope it had before the launch")
          .isEqualTo(probe.before());
    }
  }

  @Nested
  @DisplayName("on the X-Tenant-Ids route")
  class OnTheHeaderRoute {

    @Test
    @DisplayName("given a launch, should leave the request's tenant scope and clearance in place")
    void given_launchWithTheTenantIdsHeader_should_keepTheRequestScopeAfterTheLaunch()
        throws Exception {
      // -- ARRANGE --
      // Without a path tenant the scenario is looked up in the ambient tenant, which falls back to
      // the default one, so the scenario lives there. The request scope also names a second tenant
      // of the caller, so it is wider than the scenario's own tenant.
      String defaultTenant = Tenant.DEFAULT_TENANT_UUID;
      tenantHelper.grantCapabilitiesInTenant(defaultTenant, LAUNCH);
      String tenantB = tenantHelper.createTenantWithCurrentUser("launch-scope-b").getId();
      seedMarking(defaultTenant);
      String scenarioId = seedScenario(defaultTenant);
      TenantContext.clearCurrentTenant();

      // -- ACT --
      mvc.perform(
              post(SCENARIO_URI + RUNNING_EXERCISE, scenarioId)
                  .header(TENANT_IDS_HEADER, defaultTenant + "," + tenantB)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertThat(probe.before()).isNotNull();
      assertThat(probe.before().tenants()).containsExactlyInAnyOrder(defaultTenant, tenantB);
      assertThat(probe.before().markings())
          .as("the caller holds no marking grant, so its clearance is empty")
          .isEmpty();
      assertThat(probe.after())
          .as("the request continues with the scope it had before the launch")
          .isEqualTo(probe.before());
    }
  }

  // -- HELPERS --

  private String seedScenario(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenantId));
    String scenarioId = scenarioComposer.forScenario(scenario).persist().get().getId();
    entityManager.flush();
    entityManager.clear();
    return scenarioId;
  }

  private void seedMarking(String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
                + " marking_definition_definition, marking_definition_order,"
                + " marking_definition_color, marking_definition_created_at,"
                + " marking_definition_updated_at, tenant_id)"
                + " VALUES (:id, 'TLP', 'TLP:RED', 50, '#000000', now(), now(), :tenant)")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }

  /** The two scope settings of a transaction, as sets: their textual order carries no meaning. */
  record Scope(Set<String> tenants, Set<String> markings) {}

  /**
   * Reads the scope settings of the request transaction around the launch. The launch joins the
   * request's transaction, so both reads happen in that transaction.
   */
  @Aspect
  static class LaunchScopeProbe {

    private final EntityManager entityManager;
    private final List<Scope> captured = new ArrayList<>();

    LaunchScopeProbe(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Around("execution(* io.openaev.service.ScenarioToExerciseService.toExercise(..))")
    public Object aroundLaunch(ProceedingJoinPoint joinPoint) throws Throwable {
      captured.add(currentScope());
      Object result = joinPoint.proceed();
      captured.add(currentScope());
      return result;
    }

    void reset() {
      captured.clear();
    }

    Scope before() {
      return captured.isEmpty() ? null : captured.get(0);
    }

    Scope after() {
      return captured.size() < 2 ? null : captured.get(1);
    }

    private Scope currentScope() {
      return new Scope(setting("app.current_tenants"), setting("app.current_markings"));
    }

    private Set<String> setting(String name) {
      String value =
          (String)
              entityManager
                  .createNativeQuery("SELECT coalesce(current_setting(:name, true), '')")
                  .setFlushMode(FlushModeType.COMMIT)
                  .setParameter("name", name)
                  .getSingleResult();
      return value.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(value.split(",")));
    }
  }
}
