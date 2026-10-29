package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exercise.form.UpdateExerciseInput;
import io.openaev.rest.scenario.form.UpdateScenarioInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeding and requests shared by the tests of the simulation and scenario by-id reads. A by-id read
 * resolves the row inside the request's tenant scope: the path tenant on the tenant route, the
 * {@code X-Tenant-Ids} selector (or the caller's memberships) on the non-prefixed route.
 *
 * <p>Not {@code @Transactional}: rows are committed in their own tenant, so each request reads them
 * the way production does, and they are removed on teardown.
 */
abstract class ByIdRequestScopeTestBase extends IntegrationTest {

  static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired MockMvc mvc;
  @Autowired TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private PlatformTransactionManager transactionManager;

  /** A tenant the caller is a member of. */
  String memberTenant;

  /** A tenant the caller is not a member of: saved bare, since onboarding enrolls the caller. */
  String otherTenant;

  private final List<String> seededExercises = new ArrayList<>();
  private final List<String> seededScenarios = new ArrayList<>();

  @BeforeEach
  void createTenants() throws Exception {
    seededExercises.clear();
    seededScenarios.clear();
    memberTenant = tenantHelper.createTenantWithCurrentUser("by-id-member").getId();
    otherTenant =
        tenantRepository.save(TenantFixture.getTenant("by-id-other-" + UUID.randomUUID())).getId();
  }

  @AfterEach
  void cleanup() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    seededExercises.forEach(id -> jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", id));
    seededScenarios.forEach(id -> jdbc.update("DELETE FROM scenarios WHERE scenario_id = ?", id));
    tenantHelper.deleteCommittedTenants(memberTenant, otherTenant);
  }

  // -- SEEDING --

  String seedSimulation(String tenantId) {
    String id =
        inTenantScope(
            tenantId,
            () -> {
              Exercise exercise = createDefaultExercise();
              exercise.setTenant(new Tenant(tenantId));
              return exerciseRepository.save(exercise).getId();
            });
    seededExercises.add(id);
    return id;
  }

  String seedScenario(String tenantId) {
    String id =
        inTenantScope(
            tenantId,
            () -> {
              Scenario scenario = createDefaultCrisisScenario();
              scenario.setTenant(new Tenant(tenantId));
              return scenarioRepository.save(scenario).getId();
            });
    seededScenarios.add(id);
    return id;
  }

  /** Removes the caller's only membership, so a request naming no tenant resolves no scope. */
  void leaveMemberTenant() {
    String userId = testUserHolder.get().getId();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> tenantRepository.removeUserFromTenant(userId, memberTenant));
    membershipCache.evict(userId, memberTenant);
  }

  boolean simulationExists(String id) {
    return new JdbcTemplate(dataSource)
        .queryForObject("SELECT count(*) FROM exercises WHERE exercise_id = ?", Integer.class, id)
        .equals(1);
  }

  boolean scenarioExists(String id) {
    return new JdbcTemplate(dataSource)
        .queryForObject("SELECT count(*) FROM scenarios WHERE scenario_id = ?", Integer.class, id)
        .equals(1);
  }

  // -- REQUESTS --

  /** The tenant route: {@code /api/tenants/{tenantId}/...}. */
  static Route tenantRoute(String tenantId) {
    return new Route(tenantId, null);
  }

  /** The non-prefixed route with an {@code X-Tenant-Ids} selector. */
  static Route headerRoute(String selector) {
    return new Route(null, selector);
  }

  /** The non-prefixed route with no selector: the scope is the caller's memberships. */
  static Route noSelectorRoute() {
    return new Route(null, null);
  }

  record Route(String pathTenant, String headerTenant) {

    String simulation(String id) {
      return pathTenant == null
          ? EXERCISE_URI + "/" + id
          : TENANT_EXERCISE_URI.replace("{tenantId}", pathTenant) + "/" + id;
    }

    String scenario(String id) {
      return pathTenant == null
          ? SCENARIO_URI + "/" + id
          : TENANT_SCENARIO_URI.replace("{tenantId}", pathTenant) + "/" + id;
    }

    String scenarioChallenges(String id) {
      return (pathTenant == null ? "/api" : "/api/tenants/" + pathTenant)
          + "/observer/scenarios/"
          + id
          + "/challenges";
    }

    MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request) {
      if (headerTenant != null) {
        request.header(TENANT_IDS_HEADER, headerTenant);
      }
      return request.accept(MediaType.APPLICATION_JSON).with(csrf());
    }
  }

  int readSimulation(Route route, String id) throws Exception {
    return status(route.with(get(route.simulation(id) + "/communications")));
  }

  int updateSimulation(Route route, String id) throws Exception {
    UpdateExerciseInput input = new UpdateExerciseInput();
    input.setName("by-id-updated");
    return status(
        route.with(
            put(route.simulation(id))
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  int deleteSimulation(Route route, String id) throws Exception {
    return status(route.with(delete(route.simulation(id))));
  }

  int readScenario(Route route, String id) throws Exception {
    return status(route.with(get(route.scenarioChallenges(id))));
  }

  int updateScenario(Route route, String id) throws Exception {
    UpdateScenarioInput input = new UpdateScenarioInput();
    input.setName("by-id-updated");
    return status(
        route.with(
            put(route.scenario(id))
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  int deleteScenario(Route route, String id) throws Exception {
    return status(route.with(delete(route.scenario(id))));
  }

  /**
   * Performs the request from an empty ambient tenant, as production's request thread starts: the
   * fixtures leave a tenant on the test thread, which the non-prefixed route would otherwise read.
   */
  private int status(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  private <T> T inTenantScope(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
