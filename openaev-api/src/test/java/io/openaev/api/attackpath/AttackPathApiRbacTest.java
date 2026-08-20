package io.openaev.api.attackpath;

import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

  private String simulationId;
  private Authentication withGrant;
  private Authentication withoutGrant;
  private String originalDevFeatures;

  @BeforeEach
  void setUp() {
    // Store and restore platform settings around the test (the Spring context is shared).
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    Exercise simulation =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
    simulationId = simulation.getId();
    withGrant = buildAuthenticationToken(userGrantedReadOn(simulationId));
    withoutGrant = buildAuthenticationToken(plainUser());
  }

  @AfterEach
  void tearDown() {
    setDevFeatures(originalDevFeatures);
    userComposer.reset();
    grantComposer.reset();
    tenantGroupComposer.reset();
    tenantRoleComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
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
    return scenarioComposer
        .forScenario(ScenarioFixture.createDefaultCrisisScenario())
        .persist()
        .get();
  }

  // A committed simulation (exercise) attached to the scenario, with one attack-path execution row
  // so
  // it shows up in the picker summary.
  private String simWithAttackDataInScenario(Scenario scenario) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setScenario(scenario);
    String simId = exerciseComposer.forExercise(exercise).persist().get().getId();
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
    executionRepository.save(row);
  }

  private User adminUser() {
    return userComposer
        .forUser(UserFixture.getAdminUser("Ap", "Admin", UUID.randomUUID() + "@unittests.invalid"))
        .persist()
        .get();
  }

  // A user granted READ on each given simulation through resource grants only (empty-capability
  // role),
  // so the picker rows it sees come from grants, not a capability.
  private Authentication grantedReadOn(String... exerciseIds) {
    TenantGroupComposer.Composer group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withRole(
                tenantRoleComposer.forRole(TenantRoleFixture.getRole(new HashSet<>(Set.of()))));
    for (String exerciseId : exerciseIds) {
      Grant grant = new Grant();
      grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
      grant.setName(Grant.GRANT_TYPE.OBSERVER);
      grant.setResourceId(exerciseId);
      group = group.withGrant(grantComposer.forGrant(grant));
    }
    User user =
        userComposer
            .forUser(UserFixture.getUser("Ap", "Picker", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(group)
            .persist()
            .get();
    return buildAuthenticationToken(user);
  }

  private User plainUser() {
    return userComposer
        .forUser(UserFixture.getUser("Ap", "NoGrant", UUID.randomUUID() + "@unittests.invalid"))
        .persist()
        .get();
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
    return userComposer
        .forUser(UserFixture.getUser("Ap", "Granted", UUID.randomUUID() + "@unittests.invalid"))
        .withGroup(group)
        .persist()
        .get();
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
