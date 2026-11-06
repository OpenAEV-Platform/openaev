package io.openaev.rest.inject;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.atomic_testing.form.AtomicTestingUpdateTagsInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
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
 * Seeding and requests shared by the tests of the atomic testing and plain-inject by-id reads. A
 * by-id read resolves the row inside the request's tenant scope: the path tenant on the tenant
 * route, the {@code X-Tenant-Ids} selector (or the caller's memberships) on the non-prefixed route.
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
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private PlatformTransactionManager transactionManager;

  /** A tenant the caller is a member of. */
  String memberTenant;

  /** A tenant the caller is not a member of: saved bare, since onboarding enrolls the caller. */
  String otherTenant;

  private final List<String> seededInjects = new ArrayList<>();

  @BeforeEach
  void createTenants() throws Exception {
    seededInjects.clear();
    memberTenant = tenantHelper.createTenantWithCurrentUser("inj-by-id-member").getId();
    otherTenant =
        tenantRepository
            .save(TenantFixture.getTenant("inj-by-id-other-" + UUID.randomUUID()))
            .getId();
  }

  @AfterEach
  void cleanup() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    seededInjects.forEach(id -> jdbc.update("DELETE FROM injects WHERE inject_id = ?", id));
    tenantHelper.deleteCommittedTenants(memberTenant, otherTenant);
  }

  // -- SEEDING --

  /** Seeds an atomic testing (an inject with no simulation and no scenario) in the tenant. */
  String seedAtomicTesting(String tenantId) {
    String id =
        inTenantScope(
            tenantId,
            () -> {
              Inject inject = InjectFixture.getDefaultInject();
              inject.setTenant(new Tenant(tenantId));
              return injectRepository.save(inject).getId();
            });
    seededInjects.add(id);
    return id;
  }

  /** Seeds a simulation (an exercise, not an atomic testing) in the given tenant. */
  String seedExercise(String tenantId) {
    return inTenantScope(
        tenantId,
        () -> {
          Exercise exercise = ExerciseFixture.getExercise();
          exercise.setTenant(new Tenant(tenantId));
          return exerciseRepository.save(exercise).getId();
        });
  }

  /** Seeds an inject that belongs to the given exercise (not a bare atomic testing). */
  String seedInjectForExercise(String tenantId, String exerciseId) {
    return inTenantScope(
        tenantId,
        () -> {
          Inject inject = InjectFixture.getDefaultInject();
          inject.setTenant(new Tenant(tenantId));
          inject.setExercise(exerciseRepository.getReferenceById(exerciseId));
          return injectRepository.save(inject).getId();
        });
  }

  /** Seeds a scenario in the given tenant. */
  String seedScenario(String tenantId) {
    return inTenantScope(
        tenantId,
        () -> {
          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setTenant(new Tenant(tenantId));
          return scenarioRepository.save(scenario).getId();
        });
  }

  /** Seeds an inject that belongs to the given scenario. */
  String seedInjectForScenario(String tenantId, String scenarioId) {
    return inTenantScope(
        tenantId,
        () -> {
          Inject inject = InjectFixture.getDefaultInject();
          inject.setTenant(new Tenant(tenantId));
          inject.setScenario(scenarioRepository.getReferenceById(scenarioId));
          return injectRepository.save(inject).getId();
        });
  }

  String injectTitle(String injectId) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT inject_title FROM injects WHERE inject_id = ?", String.class, injectId);
  }

  boolean atomicTestingExists(String id) {
    return count("SELECT count(*) FROM injects WHERE inject_id = ?", id) == 1;
  }

  private int count(String sql, Object... args) {
    return new JdbcTemplate(dataSource).queryForObject(sql, Integer.class, args);
  }

  /** Removes the caller's only membership, so a request naming no tenant resolves no scope. */
  void leaveMemberTenant() {
    String userId = testUserHolder.get().getId();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> tenantRepository.removeUserFromTenant(userId, memberTenant));
  }

  <T> T inTenantScope(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
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

    String atomicTesting(String id) {
      return pathTenant == null
          ? ATOMIC_TESTING_URI + "/" + id
          : TENANT_ATOMIC_TESTING_URI.replace("{tenantId}", pathTenant) + "/" + id;
    }

    String inject(String id) {
      return pathTenant == null
          ? INJECT_URI + "/" + id
          : TENANT_INJECT_URI.replace("{tenantId}", pathTenant) + "/" + id;
    }

    MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request) {
      if (headerTenant != null) {
        request.header(TENANT_IDS_HEADER, headerTenant);
      }
      return request.accept(MediaType.APPLICATION_JSON).with(csrf());
    }
  }

  /**
   * Performs the request from an empty ambient tenant, as production's request thread starts: the
   * fixtures leave a tenant on the test thread, which the non-prefixed route would otherwise read.
   */
  int status(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  int getAtomicTesting(Route route, String id) throws Exception {
    return status(route.with(get(route.atomicTesting(id))));
  }

  int getAtomicTestingPayload(Route route, String id) throws Exception {
    return status(route.with(get(route.atomicTesting(id) + "/payload")));
  }

  int updateAtomicTestingTags(Route route, String id) throws Exception {
    AtomicTestingUpdateTagsInput input = new AtomicTestingUpdateTagsInput();
    input.setTagIds(List.of());
    return status(
        route.with(
            put(route.atomicTesting(id) + "/tags")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  int deleteAtomicTesting(Route route, String id) throws Exception {
    return status(route.with(delete(route.atomicTesting(id))));
  }

  /**
   * {@code POST /atomic-testings/{injectId}/expectations-drift/realign}: {@code SUPPORTS}
   * propagation, so the by-id scope guard carries its own transaction.
   */
  int realignAtomicTesting(Route route, String id) throws Exception {
    return status(route.with(post(route.atomicTesting(id) + "/expectations-drift/realign")));
  }

  int getInject(Route route, String id) throws Exception {
    return status(route.with(get(route.inject(id))));
  }

  /** {@code GET /injects/execution-traces}: the by-id scope guard runs ahead of the trace query. */
  int injectExecutionTraces(Route route, String injectId, String targetId, String targetType)
      throws Exception {
    String uri = injectsBaseUri(route) + "/execution-traces";
    return status(
        route.with(
            get(uri)
                .param("injectId", injectId)
                .param("targetId", targetId)
                .param("targetType", targetType)));
  }

  String injectsBaseUri(Route route) {
    return route.pathTenant() == null
        ? INJECT_URI
        : TENANT_INJECT_URI.replace("{tenantId}", route.pathTenant());
  }
}
