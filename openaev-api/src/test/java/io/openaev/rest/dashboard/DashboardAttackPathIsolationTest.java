package io.openaev.rest.dashboard;

import static io.openaev.rest.dashboard.DashboardApi.DASHBOARD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CustomDashboardFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.WidgetFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves, through the real attack-path endpoint, that the one JPA read of {@code
 * EsAttackPathService} is restricted to the request's tenant scope.
 *
 * <p>The read is {@code attackPatternRepository.findAllById(ids)}, where the ids come from
 * Elasticsearch, not from the database: the attack-path response names the patterns carried by the
 * simulation's indexed injects. Nothing but the scope on the request thread keeps that lookup
 * inside the caller's tenant, so the probe is a simulation in tenant A whose injector contract
 * links one pattern of tenant A and one of tenant B. Both ids reach Elasticsearch (the indexing
 * query reads the join table, which carries no tenant of its own), so both are handed to the lookup
 * and only the scope decides what comes back.
 *
 * <p>This is the behavioural replacement for the baseline assertion that used to pin an {@code
 * until-active:attack_patterns} tag on {@code EsAttackPathService}: the tag expired when the table
 * activated, and what it was protecting is now asserted here instead of spelled in a waiver.
 *
 * <p>{@code attack_patterns} is armed alone so the no-property control differs from this run by
 * that table and nothing else.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.tenant.active-tables=attack_patterns")
@DisplayName("Attack-path dashboard attack-pattern read isolation")
class DashboardAttackPathIsolationTest extends IntegrationTest {

  private static final String ATTACK_PATHS = DASHBOARD_URI + "/attack-paths/";
  private static final String TENANT_ATTACK_PATHS = "/api/tenants/%s/dashboards/attack-paths/%s";

  @Autowired private MockMvc mvc;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private WidgetComposer widgetComposer;

  private String tenantA;
  private AttackPattern patternA;
  private AttackPattern patternB;
  private String widgetId;

  @BeforeEach
  void seedOneSimulationReachingTwoTenantsPatterns() throws Exception {
    endpointComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    widgetComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }

    // The mock admin user carries no users_tenants row by default, so without this the request
    // scope would resolve to missing() and the engine read would see nothing at all.
    tenantA = TenantContext.getCurrentTenant();
    tenantIsolationHelper.attachCurrentUserToTenant(tenantA);
    String tenantB = tenantIsolationHelper.createTenantWithCurrentUser("attack-path-iso-b").getId();

    patternA = AttackPatternFixture.createAttackPatternsWithExternalId("TAP9001", tenantA);
    patternB = AttackPatternFixture.createAttackPatternsWithExternalId("TAP9002", tenantB);

    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    InjectorContractComposer.Composer contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withAttackPattern(attackPatternComposer.forAttackPattern(patternA))
            .withAttackPattern(attackPatternComposer.forAttackPattern(patternB));
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(endpoint)
            .withInjectorContract(contract)
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                            BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withEndpoint(endpoint));
    Exercise exercise =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(inject)
            .persist()
            .get();

    widgetId =
        widgetComposer
            .forWidget(
                WidgetFixture.createAttackPathWidget(
                    exercise.getId(), BaseInjectExpectation.EXPECTATION_TYPE.DETECTION))
            .withCustomDashboard(
                customDashboardComposer.forCustomDashboard(
                    CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
            .persist()
            .get()
            .getId();

    indexEveryTenant();
  }

  @Nested
  @DisplayName("Reads on the tenant path route")
  class PathRouteReads {

    @Test
    @DisplayName("given A's path, the attack paths should name A's pattern and not B's")
    void given_tenantAPath_should_resolveOnlyOwnAttackPattern() throws Exception {
      // Arrange / Act
      String response = attackPaths(post(TENANT_ATTACK_PATHS.formatted(tenantA, widgetId)));

      // Assert
      assertOnlyOwnPatternIsNamed(response);
    }
  }

  @Nested
  @DisplayName("Reads on the X-Tenant-Ids header route")
  class HeaderRouteReads {

    @Test
    @DisplayName("given the A header, the attack paths should name A's pattern and not B's")
    void given_tenantAHeader_should_resolveOnlyOwnAttackPattern() throws Exception {
      // Arrange / Act
      String response = attackPaths(post(ATTACK_PATHS + widgetId).header("X-Tenant-Ids", tenantA));

      // Assert
      assertOnlyOwnPatternIsNamed(response);
    }
  }

  /**
   * The positive half is asserted first on purpose: an endpoint that returned nothing at all would
   * satisfy the negative half on its own, and that is the shape a fail-closed read without a scope
   * takes.
   */
  private void assertOnlyOwnPatternIsNamed(String response) {
    assertTrue(
        response.contains(patternA.getId()),
        "the simulation's own attack pattern must be named in the attack paths: " + response);
    assertTrue(
        response.contains(patternA.getExternalId()),
        "the own pattern must be resolved from the database, external id included: " + response);
    assertFalse(
        response.contains(patternB.getId()),
        "another tenant's attack pattern id must not be resolved: " + response);
    assertFalse(
        response.contains(patternB.getExternalId()),
        "another tenant's attack pattern must not reach the response: " + response);
  }

  private String attackPaths(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    return mvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new HashMap<String, String>()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * Indexes the way production does: the sweep runs under {@code TxCtx.allTenants()}, and calling
   * {@code bulkProcessing} with no scope would read nothing on an activated table. The scope is
   * released afterwards with the raw setting rather than the primitive, because what the HTTP
   * request that follows needs is the neutral state a fresh transaction has: it sets its own scope,
   * and the aspect refuses to redefine one that is already set.
   */
  private void indexEveryTenant() throws InterruptedException {
    entityManager.flush();
    entityManager.clear();
    tenantTx.setScopeOnCurrentTransaction(TxCtx.allTenants());
    try {
      engineService.bulkProcessing(engineContext.getModels().stream());
    } finally {
      entityManager
          .createNativeQuery("SELECT set_config('app.current_tenants', '', true)")
          .getSingleResult();
    }
    // Elasticsearch indexes asynchronously, so the documents are not searchable on return.
    Thread.sleep(1000);
  }
}
