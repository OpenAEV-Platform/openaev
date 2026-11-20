package io.openaev.api.attackpath;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * B1 (#6647 hardening): the attack-path read endpoints must enforce resource-level RBAC on real
 * simulations, while a synthetic seed id ({@code ap-seed-…}, no real exercise) stays reachable.
 * Real stack (MockMvc + real PG + real grants), no mocked RBAC.
 */
@DisplayName("attack path: read endpoints enforce SIMULATION READ (seed-tolerant)")
class AttackPathApiRbacTest extends IntegrationTest {

  private static final String BASE = "/api/attack-path/simulations/";

  @Autowired private MockMvc mvc;
  @Autowired private io.openaev.config.OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private UserRepository userRepository;
  @Autowired private ExerciseRepository exerciseRepository;

  // The ids of the rows a test commits, deleted after it.
  private final List<String> grantIds = new ArrayList<>();
  private final List<String> groupIds = new ArrayList<>();
  private final List<String> roleIds = new ArrayList<>();
  private final List<String> userIds = new ArrayList<>();
  private final List<String> executionIds = new ArrayList<>();
  private final List<String> exerciseIds = new ArrayList<>();
  private final List<String> scenarioIds = new ArrayList<>();

  private String simulationId;
  private Authentication withGrant;
  private Authentication withoutGrant;
  private String originalDevFeatures;

  @BeforeEach
  void setUp() {
    // Store and restore platform settings around the test (the Spring context is shared).
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    Exercise simulation =
        inDefaultTenant(
            () ->
                exerciseComposer
                    .forExercise(ExerciseFixture.createDefaultExercise())
                    .persist()
                    .get());
    simulationId = simulation.getId();
    exerciseIds.add(simulationId);
    withGrant = buildAuthenticationToken(userGrantedReadOn(simulationId));
    withoutGrant = buildAuthenticationToken(plainUser());
  }

  @AfterEach
  void tearDown() {
    // The class is not @Transactional: every fixture row is committed, so each test deletes its
    // own, under the default tenant's scope they were written in. Every step runs even when an
    // earlier one fails, so the settings and composers are restored in any case.
    runAll(
        () -> deleteInDefaultTenant(Grant.class, grantIds),
        () -> deleteInDefaultTenant(Group.class, groupIds),
        () -> deleteInDefaultTenant(Role.class, roleIds),
        () -> deleteUsersInDefaultTenant(userIds),
        () -> deleteInDefaultTenant(AttackPathExecution.class, executionIds),
        () -> deleteInDefaultTenant(() -> exerciseIds.forEach(exerciseRepository::deleteById)),
        () -> deleteInDefaultTenant(Scenario.class, scenarioIds),
        () -> setDevFeatures(originalDevFeatures),
        userComposer::reset,
        grantComposer::reset,
        tenantGroupComposer::reset,
        tenantRoleComposer::reset,
        exerciseComposer::reset,
        scenarioComposer::reset);
  }

  // Runs every step in order, whatever an earlier one throws: the first failure is rethrown at the
  // end, the others are attached to it as suppressed.
  private static void runAll(Runnable... steps) {
    Throwable failure = null;
    for (Runnable step : steps) {
      try {
        step.run();
      } catch (Throwable e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw new IllegalStateException("tearDown failed", failure);
    }
  }

  // -- the 5 reads, keyed by simulationId, with the params each requires so the request reaches the
  // controller body (a missing required param would 400 before RBAC). --
  private MockHttpServletRequestBuilder graph(String simId) {
    return get(BASE + simId + "/graph");
  }

  private MockHttpServletRequestBuilder graphDelta(String simId) {
    return get(BASE + simId + "/graph/delta").param("since", "0");
  }

  private MockHttpServletRequestBuilder endpointFindings(String simId) {
    return get(BASE + simId + "/endpoint/findings").param("ref", "any");
  }

  private MockHttpServletRequestBuilder endpointRelations(String simId) {
    return get(BASE + simId + "/endpoint/relations").param("ref", "any");
  }

  private MockHttpServletRequestBuilder findings(String simId) {
    return get(BASE + simId + "/findings").param("category", "credentials");
  }

  private MockHttpServletRequestBuilder execution(String simId) {
    return get(BASE + simId + "/execution").param("ref", "any");
  }

  // The per-simulation reads, all guarded by the same assertCanReadSimulation. The delta cursor is
  // one of them: it is polled every few seconds, so an unguarded one would be the widest hole here.
  private List<MockHttpServletRequestBuilder> reads(String simId) {
    return List.of(
        graph(simId),
        graphDelta(simId),
        endpointFindings(simId),
        endpointRelations(simId),
        findings(simId),
        execution(simId));
  }

  @Test
  @DisplayName("Without a READ grant on the simulation, every read is 403")
  void noGrant_allReadsForbidden() throws Exception {
    for (MockHttpServletRequestBuilder read : reads(simulationId)) {
      mvc.perform(read.with(authentication(withoutGrant)).with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName(
      "With a READ grant, no read is forbidden (the guard is wired on every read, not just one)")
  void withGrant_noReadForbidden() throws Exception {
    // The guard passed on each read: the status is whatever the read returns (200, or 404 for a
    // ref/category with no data), never 403. Asserting NOT-403 on all 5 catches a mis-wired
    // endpoint
    // that would wrongly deny a granted user; a status-200 on graph (below) pins the real allow
    // path.
    for (MockHttpServletRequestBuilder read : reads(simulationId)) {
      mvc.perform(read.with(authentication(withGrant)).with(csrf()))
          .andExpect(r -> assertNotEquals(403, r.getResponse().getStatus()));
    }
  }

  @Test
  @DisplayName("With a READ grant on the simulation, the graph read is allowed (real allow path)")
  void withGrant_graphAllowed() throws Exception {
    mvc.perform(graph(simulationId).with(authentication(withGrant)).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("A synthetic seed id stays reachable without a grant")
  void seedId_reachableWithoutGrant() throws Exception {
    String seedId = AttackPathIds.SEED_ID_PREFIX + "1-sim-0";
    mvc.perform(graph(seedId).with(authentication(withoutGrant)).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Picker with a scenarioId returns only that scenario's simulations (B0)")
  void picker_scopedToScenario() throws Exception {
    Scenario s1 = scenario();
    Scenario s2 = scenario();
    String sim1 = simWithAttackDataInScenario(s1);
    String sim2 = simWithAttackDataInScenario(s2);
    // Granted on BOTH, so what filters the list is the scenario scope, not the grants.
    Authentication user = grantedReadOn(sim1, sim2);

    List<String> ids =
        pickerSimulationIds(
            get("/api/attack-path/simulations").param("scenarioId", s1.getId()), user);

    assertThat(ids).contains(sim1).doesNotContain(sim2);
  }

  @Test
  @DisplayName("Picker grant-filters: a user sees only the simulations it can read (B0)")
  void picker_grantFiltered() throws Exception {
    String sim1 = simWithAttackDataInScenario(scenario());
    String sim2 = simWithAttackDataInScenario(scenario());
    Authentication user = grantedReadOn(sim1); // granted on sim1 only

    List<String> ids = pickerSimulationIds(get("/api/attack-path/simulations"), user);

    assertThat(ids).contains(sim1).doesNotContain(sim2);
  }

  @Test
  @DisplayName("Picker keeps synthetic seed rows for any caller (FR3)")
  void picker_keepsSeedRows() throws Exception {
    String seedSim = AttackPathIds.SEED_ID_PREFIX + "9-sim-0";
    saveExecutionRow(seedSim);
    // A caller with no grant: the seed row is kept (seed-tolerant), even though it is granted
    // nothing.
    List<String> ids = pickerSimulationIds(get("/api/attack-path/simulations"), withoutGrant);
    assertThat(ids).contains(seedSim);
  }

  @Test
  @DisplayName(
      "An admin is unchanged: reads not forbidden and the picker is not grant-filtered (FR5/FR6f)")
  void admin_unchanged() throws Exception {
    String sim1 = simWithAttackDataInScenario(scenario());
    String sim2 = simWithAttackDataInScenario(scenario());
    Authentication admin = buildAuthenticationToken(adminUser());

    // Reads: an admin bypasses grants, so none of the 5 is forbidden.
    for (MockHttpServletRequestBuilder read : reads(simulationId)) {
      mvc.perform(read.with(authentication(admin)).with(csrf()))
          .andExpect(r -> assertNotEquals(403, r.getResponse().getStatus()));
    }
    // Picker: an admin sees every simulation, not a grant-filtered subset.
    List<String> ids = pickerSimulationIds(get("/api/attack-path/simulations"), admin);
    assertThat(ids).contains(sim1, sim2);
  }

  private List<String> pickerSimulationIds(MockHttpServletRequestBuilder request, Authentication as)
      throws Exception {
    String body =
        mvc.perform(request.with(authentication(as)).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$..simulationId");
  }

  // -- fixtures --

  private Scenario scenario() {
    Scenario scenario =
        inDefaultTenant(
            () ->
                scenarioComposer
                    .forScenario(ScenarioFixture.createDefaultCrisisScenario())
                    .persist()
                    .get());
    scenarioIds.add(scenario.getId());
    return scenario;
  }

  // A committed simulation (exercise) attached to the scenario, with one attack-path execution row
  // so
  // it shows up in the picker summary.
  private String simWithAttackDataInScenario(Scenario scenario) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setScenario(scenario);
    String simId =
        inDefaultTenant(() -> exerciseComposer.forExercise(exercise).persist().get()).getId();
    exerciseIds.add(simId);
    saveExecutionRow(simId);
    return simId;
  }

  // One attack-path execution row for a simulation id (a real exercise or a seed id), so it
  // surfaces
  // in the picker summary.
  private void saveExecutionRow(String simulationId) {
    AttackPathExecution row = new AttackPathExecution();
    row.setId(UUID.randomUUID().toString());
    row.setSimulationId(simulationId);
    row.setSourceKind("AGENT_ASSET");
    row.setTargetKind("ASSET");
    row.setTargetKey("target-" + simulationId);
    row.setExecutedAt(Instant.now());
    inDefaultTenant(() -> executionRepository.save(row));
    executionIds.add(row.getId());
  }

  private User adminUser() {
    return memberOfDefaultTenant(
        userComposer.forUser(
            UserFixture.getAdminUser("Ap", "Admin", UUID.randomUUID() + "@unittests.invalid")));
  }

  // A user granted READ on each given simulation through resource grants only (empty-capability
  // role),
  // so the picker rows it sees come from grants, not a capability.
  private Authentication grantedReadOn(String... grantedExerciseIds) {
    TenantGroupComposer.Composer group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withRole(
                tenantRoleComposer.forRole(TenantRoleFixture.getRole(new HashSet<>(Set.of()))));
    for (String exerciseId : grantedExerciseIds) {
      Grant grant = new Grant();
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
      grant.setName(Grant.GRANT_TYPE.OBSERVER);
      grant.setResourceId(exerciseId);
      group = group.withGrant(grantComposer.forGrant(grant));
    }
    User user =
        memberOfDefaultTenant(
            userComposer
                .forUser(
                    UserFixture.getUser("Ap", "Picker", UUID.randomUUID() + "@unittests.invalid"))
                .withGroup(group));
    return buildAuthenticationToken(user);
  }

  private User plainUser() {
    return memberOfDefaultTenant(
        userComposer.forUser(
            UserFixture.getUser("Ap", "NoGrant", UUID.randomUUID() + "@unittests.invalid")));
  }

  // A user granted READ on the simulation ONLY through a resource grant — deliberately NO
  // assessment
  // capability (empty-capability role), so a 200 proves the resource grant is what grants access,
  // not
  // a capability. PermissionService checks the capability first, then the grant, so the capability
  // would otherwise mask the grant path this test must exercise.
  private User userGrantedReadOn(String exerciseId) {
    Grant grant = new Grant();
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
    grant.setName(Grant.GRANT_TYPE.OBSERVER);
    grant.setResourceId(exerciseId);
    TenantGroupComposer.Composer group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withRole(
                tenantRoleComposer.forRole(TenantRoleFixture.getRole(new HashSet<>(Set.of()))))
            .withGrant(grantComposer.forGrant(grant));
    return memberOfDefaultTenant(
        userComposer
            .forUser(UserFixture.getUser("Ap", "Granted", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(group));
  }

  // The class is not @Transactional, so each fixture write commits on its own. It runs under the
  // default tenant's scope, as the write of a request in that tenant does.
  private <T> T inDefaultTenant(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(DEFAULT_TENANT_UUID), work);
  }

  // A set-based delete by id: no entity is loaded, so no lazy collection is read. For a group or a
  // role, Hibernate clears the link rows the entity owns whose foreign keys do not cascade
  // (groups_roles, roles_capabilities) before the row itself; users_groups and groups_markings go
  // with the group through ON DELETE CASCADE. The count must equal the ids: a scope that stops
  // matching would delete nothing, silently.
  private void deleteInDefaultTenant(Class<?> entity, List<String> ids) {
    List<String> distinctIds = ids.stream().distinct().toList();
    if (distinctIds.isEmpty()) {
      return;
    }
    deleteInDefaultTenant(
        () -> {
          int deleted =
              entityManager
                  .createQuery("DELETE FROM " + entity.getSimpleName() + " e WHERE e.id IN :ids")
                  .setParameter("ids", distinctIds)
                  .executeUpdate();
          assertEquals(distinctIds.size(), deleted, entity.getSimpleName() + " rows deleted");
        });
  }

  // Only the memberships (users_tenants, users_groups) go with a deleted user, through ON DELETE
  // CASCADE; the user's groups, roles and grants are deleted by their own ids.
  private void deleteUsersInDefaultTenant(List<String> ids) {
    List<String> distinctIds = ids.stream().distinct().toList();
    deleteInDefaultTenant(
        () -> {
          int deleted = distinctIds.stream().mapToInt(userRepository::deleteByIdNative).sum();
          assertEquals(distinctIds.size(), deleted, "User rows deleted");
        });
  }

  private void deleteInDefaultTenant(Runnable delete) {
    tenantTx.execute(TxCtx.forTenant(DEFAULT_TENANT_UUID), delete);
  }

  // A caller of the default tenant: its user, groups, roles and grants are written in that tenant,
  // and it is a member of it, as every real user is of the tenants it works in.
  private User memberOfDefaultTenant(UserComposer.Composer user) {
    User persisted = inDefaultTenant(() -> user.persist().get());
    // Recorded before the membership is added, so a failure there still leaves the rows deleted.
    userIds.add(persisted.getId());
    for (Group group : persisted.getUnscopedGroups()) {
      groupIds.add(group.getId());
      group.getRoles().stream().map(Role::getId).forEach(roleIds::add);
      group.getGrants().stream().map(Grant::getId).forEach(grantIds::add);
    }
    tenantHelper.attachUserToTenant(persisted.getId(), DEFAULT_TENANT_UUID);
    return persisted;
  }

  // Sets the raw dev-features value (may be the stored original, which can be null) and evicts the
  // @Cacheable("global") isFeatureEnabled cache so the change is observed.
  private void setDevFeatures(String value) {
    openAEVConfig.setEnabledDevFeatures(value);
    if (cacheManager.getCache("global") != null) {
      cacheManager.getCache("global").clear();
    }
  }
}
