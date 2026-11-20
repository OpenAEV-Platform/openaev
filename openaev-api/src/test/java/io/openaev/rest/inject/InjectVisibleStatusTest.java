package io.openaev.rest.inject;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject status a reader is shown, from the agents their clearance lets them see: the RED agent
 * succeeded, the GREEN one failed, so the stored verdict is PARTIAL. A GREEN reader sees only the
 * GREEN agent and reads ERROR; a RED reader sees both and reads the stored PARTIAL. Checked on the
 * atomic testings and on the simulation injects.
 *
 * <p>Markings are seeded with raw JDBC for the reason given in {@code AssetMarkingIsolationTest}.
 */
@Transactional
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
@DisplayName("Inject status shown from the agents the reader can see")
class InjectVisibleStatusTest extends IntegrationTest {

  private static final String ATOMIC_TESTING_SEARCH_URI = ATOMIC_TESTING_URI + "/search";
  private static final String INJECT_STATUS_URI = INJECT_URI + "/status";
  private static final int STATEMENT_COUNT_RUNS = 3;

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  // A mock, not a spy: isFeatureEnabled is @Cacheable, so a spy's stub goes through the cache
  @MockitoBean private PreviewFeatureService previewFeatureService;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;
  private Group group;
  private MarkingDefinition tlpGreen;
  private MarkingDefinition tlpRed;
  private final List<Endpoint> redEndpoints = new ArrayList<>();
  private final List<Endpoint> greenEndpoints = new ArrayList<>();

  @BeforeEach
  void seed() {
    // As the test properties: MARKING only
    when(previewFeatureService.isFeatureEnabled(any()))
        .thenAnswer(call -> call.getArgument(0) == PreviewFeature.MARKING);
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    injectStatusComposer.reset();
    executionTraceComposer.reset();
    exerciseComposer.reset();
    redEndpoints.clear();
    greenEndpoints.clear();

    tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    tlpGreen = marking(60);
    tlpRed = marking(70);
    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("visible-status-" + uniqueDefinition()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);
    // The grants are raw JDBC: the group must be in the database, not just the persistence context
    entityManager.flush();
  }

  @Nested
  @DisplayName("with a TLP:GREEN clearance")
  class GreenReader {

    @BeforeEach
    void grantGreen() {
      grant(tlpGreen);
    }

    @Test
    @DisplayName("given the atomic testing list, should show the status of the GREEN agent only")
    void given_search_should_showTheVisibleStatus() throws Exception {
      // Arrange
      Inject inject = partialInjectOnRedAndGreen();

      // Act
      String response = search();

      // Assert
      assertThat(statusInPage(response, inject)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("given the atomic testing page, should show the status of the GREEN agent only")
    void given_atomicTesting_should_showTheVisibleStatus() throws Exception {
      // Arrange
      Inject inject = partialInjectOnRedAndGreen();

      // Act
      String response = getJson(ATOMIC_TESTING_URI + "/" + inject.getId());

      // Assert
      assertThat(JsonPath.<String>read(response, "$.inject_status.status_name")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("given the execution details, should show the status of the GREEN agent only")
    void given_injectStatus_should_showTheVisibleStatus() throws Exception {
      // Arrange
      Inject inject = partialInjectOnRedAndGreen();

      // Act
      String response = getJson(INJECT_STATUS_URI + "?injectId=" + inject.getId());

      // Assert
      assertThat(JsonPath.<String>read(response, "$.status_name")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("given every agent hidden, should show NOT_VISIBLE, not the outcome on them")
    void given_everyAgentHidden_should_showNotVisible() throws Exception {
      // Arrange: two RED agents, both succeeded
      Inject inject =
          injectWithRuns(
              InjectStatusFixture.createSuccessStatus(),
              List.of(
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete()),
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete())),
              null);

      // Act
      String page = search();
      String atomicTesting = getJson(ATOMIC_TESTING_URI + "/" + inject.getId());
      String injectStatus = getJson(INJECT_STATUS_URI + "?injectId=" + inject.getId());

      // Assert
      assertThat(statusInPage(page, inject)).isEqualTo("NOT_VISIBLE");
      assertThat(JsonPath.<String>read(atomicTesting, "$.inject_status.status_name"))
          .isEqualTo("NOT_VISIBLE");
      assertThat(JsonPath.<String>read(injectStatus, "$.status_name")).isEqualTo("NOT_VISIBLE");
    }

    @Test
    @DisplayName(
        "given the simulation injects, should show the status of the agents the reader sees")
    void given_simulationInjects_should_showTheVisibleStatus() throws Exception {
      // Arrange: a PARTIAL inject on RED and GREEN, and an inject that succeeded on two RED
      // agents, each in its own simulation
      ExerciseComposer.Composer simulation = simulation();
      Inject partial = partialInjectOnRedAndGreen(simulation);
      ExerciseComposer.Composer redSimulation = simulation();
      Inject onRedOnly =
          injectWithRuns(
              InjectStatusFixture.createSuccessStatus(),
              List.of(
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete()),
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete())),
              null,
              redSimulation);

      // Act
      String injects = getJson(simulationInjectsUri(simulation.get()));
      String redInjects = getJson(simulationInjectsUri(redSimulation.get()));
      String single = getJson(INJECT_URI + "/" + partial.getId());

      // Assert
      assertThat(statusInList(injects, partial)).isEqualTo("ERROR");
      assertThat(statusInList(redInjects, onRedOnly)).isEqualTo("NOT_VISIBLE");
      assertThat(JsonPath.<String>read(single, "$.inject_status.status_name")).isEqualTo("ERROR");
      assertThat(JsonPath.<String>read(single, "$.inject_title")).isEqualTo(partial.getTitle());
    }

    @Test
    @DisplayName("given a status set by hand, with no agent dispatched, should show it")
    void given_statusSetByHand_should_showTheStoredStatus() throws Exception {
      // Arrange: no trace for anyone, so nothing is hidden from this reader
      Inject inject = injectWithRuns(InjectStatusFixture.createSuccessStatus(), List.of(), null);

      // Act
      String page = search();

      // Assert
      assertThat(statusInPage(page, inject)).isEqualTo("EXECUTED");
    }

    @Test
    @DisplayName(
        "given an inject closed by the platform after a hidden success, should show the platform's"
            + " ERROR, not the stored PARTIAL")
    void given_closedByPlatformAfterHiddenSuccess_should_showThePlatformError() throws Exception {
      // Arrange: the RED agent succeeded, then the platform closed the inject with an error trace
      // of
      // its own: the stored verdict counts both
      InjectStatus status = InjectStatusFixture.createSuccessStatus();
      status.setName(ExecutionStatus.PARTIAL);
      Inject inject =
          injectWithRuns(
              status,
              List.of(new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete())),
              ExecutionTraceFixture.createDefaultExecutionTraceError());

      // Act
      String page = search();

      // Assert
      assertThat(statusInPage(page, inject)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("given closed injects in the page, should recompute them in one query")
    void given_closedInjects_should_recomputeInOneQuery() throws Exception {
      // Arrange: the same page, only the stored statuses change between the reads, so the
      // difference is the recompute alone
      List<Inject> injects =
          List.of(
              partialInjectOnRedAndGreen(),
              partialInjectOnRedAndGreen(),
              partialInjectOnRedAndGreen());
      search();
      storeStatus(injects, "PENDING");
      long noneClosed = statementsOf(() -> searchUnchecked());
      storeStatus(injects.subList(0, 1), "PARTIAL");
      long oneClosed = statementsOf(() -> searchUnchecked());
      storeStatus(injects, "PARTIAL");

      // Act
      long threeClosed = statementsOf(() -> searchUnchecked());

      // Assert
      assertThat(oneClosed).as("one query for the closed injects").isEqualTo(noneClosed + 1);
      assertThat(threeClosed)
          .as("the recompute must not scale with the closed rows")
          .isEqualTo(oneClosed);
    }
  }

  @Nested
  @DisplayName("with a TLP:RED clearance")
  class RedReader {

    @BeforeEach
    void grantRed() {
      grant(tlpRed);
    }

    @Test
    @DisplayName("given every agent visible, should show the stored PARTIAL everywhere")
    void given_everyAgentVisible_should_showTheStoredStatus() throws Exception {
      // Arrange
      Inject inject = partialInjectOnRedAndGreen();

      // Act
      String page = search();
      String atomicTesting = getJson(ATOMIC_TESTING_URI + "/" + inject.getId());
      String injectStatus = getJson(INJECT_STATUS_URI + "?injectId=" + inject.getId());

      // Assert
      assertThat(statusInPage(page, inject)).isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(atomicTesting, "$.inject_status.status_name"))
          .isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(injectStatus, "$.status_name")).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("given RED agents only, should show their outcome")
    void given_redAgentsOnly_should_showTheStoredStatus() throws Exception {
      // Arrange
      Inject inject =
          injectWithRuns(
              InjectStatusFixture.createSuccessStatus(),
              List.of(
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete()),
                  new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete())),
              null);

      // Act
      String page = search();

      // Assert
      assertThat(statusInPage(page, inject)).isEqualTo("EXECUTED");
    }

    @Test
    @DisplayName("given the simulation injects, should show the stored PARTIAL")
    void given_simulationInjects_should_showTheStoredStatus() throws Exception {
      // Arrange
      ExerciseComposer.Composer simulation = simulation();
      Inject inject = partialInjectOnRedAndGreen(simulation);

      // Act
      String injects = getJson(simulationInjectsUri(simulation.get()));
      String single = getJson(INJECT_URI + "/" + inject.getId());

      // Assert
      assertThat(statusInList(injects, inject)).isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(single, "$.inject_status.status_name")).isEqualTo("PARTIAL");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT})
    @DisplayName("given NOT_VISIBLE set by hand, should refuse it and keep the stored status")
    void given_notVisibleSetByHand_should_refuseIt() throws Exception {
      // Arrange
      ExerciseComposer.Composer simulation = simulation();
      Inject inject = partialInjectOnRedAndGreen(simulation);

      // Act
      mvc.perform(
              post(simulationInjectsUri(simulation.get()) + "/" + inject.getId() + "/status")
                  .content(asJsonString(Map.of("status", "NOT_VISIBLE")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // Assert
          .andExpect(status().isBadRequest());
      assertThat(
              jdbc.queryForObject(
                  "SELECT status_name FROM injects_statuses WHERE status_inject = ?",
                  String.class,
                  inject.getId()))
          .isEqualTo("PARTIAL");
    }
  }

  @Nested
  @DisplayName("with the MARKING preview feature off")
  class MarkingOff {

    @BeforeEach
    void turnMarkingOff() {
      grant(tlpGreen);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(false);
    }

    @Test
    @DisplayName("given a status its traces do not explain, should show the stored one everywhere")
    void given_markingOff_should_showTheStoredStatus() throws Exception {
      // Arrange
      Inject atomicTesting = partialInjectWithOneTrace(null);
      ExerciseComposer.Composer simulation = simulation();
      Inject simulationInject = partialInjectWithOneTrace(simulation);

      // Act
      String page = search();
      String atomicTestingPage = getJson(ATOMIC_TESTING_URI + "/" + atomicTesting.getId());
      String injectStatus = getJson(INJECT_STATUS_URI + "?injectId=" + atomicTesting.getId());
      String simulationInjects = getJson(simulationInjectsUri(simulation.get()));
      String single = getJson(INJECT_URI + "/" + simulationInject.getId());

      // Assert
      assertThat(statusInPage(page, atomicTesting)).isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(atomicTestingPage, "$.inject_status.status_name"))
          .isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(injectStatus, "$.status_name")).isEqualTo("PARTIAL");
      assertThat(statusInList(simulationInjects, simulationInject)).isEqualTo("PARTIAL");
      assertThat(JsonPath.<String>read(single, "$.inject_status.status_name")).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("given closed injects in the page, should run no recompute query")
    void given_closedInjects_should_runNoRecomputeQuery() throws Exception {
      // Arrange: the same page, only the stored statuses change between the reads
      List<Inject> injects = List.of(partialInjectOnRedAndGreen(), partialInjectOnRedAndGreen());
      search();
      storeStatus(injects, "PENDING");
      long noneClosed = statementsOf(() -> searchUnchecked());
      storeStatus(injects, "PARTIAL");

      // Act
      long closed = statementsOf(() -> searchUnchecked());

      // Assert
      assertThat(closed).as("no query for the closed injects").isEqualTo(noneClosed);
    }
  }

  // -- HELPERS --

  /** An atomic testing on a RED and a GREEN endpoint: the RED agent succeeded, the GREEN failed. */
  private Inject partialInjectOnRedAndGreen() {
    return partialInjectOnRedAndGreen(null);
  }

  /** The same inject, in the given simulation (none: an atomic testing). */
  private Inject partialInjectOnRedAndGreen(ExerciseComposer.Composer simulation) {
    return injectWithRuns(
        InjectStatusFixture.createPartialInjectStatus(),
        List.of(
            new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete()),
            new Run(tlpGreen, ExecutionTraceFixture.createDefaultExecutionTraceError())),
        null,
        simulation);
  }

  /**
   * A PARTIAL status over two agents, with the COMPLETE trace of the GREEN agent only: a recompute
   * reads ERROR whatever the clearance, the stored status reads PARTIAL.
   */
  private Inject partialInjectWithOneTrace(ExerciseComposer.Composer simulation) {
    Inject inject =
        injectWithRuns(
            InjectStatusFixture.createPartialInjectStatus(),
            List.of(new Run(tlpGreen, ExecutionTraceFixture.createDefaultExecutionTraceError())),
            null,
            simulation);
    jdbc.update(
        "UPDATE injects_statuses SET status_expected_agent_count = 2 WHERE status_inject = ?",
        inject.getId());
    return inject;
  }

  private ExerciseComposer.Composer simulation() {
    return exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
  }

  private static String simulationInjectsUri(Exercise simulation) {
    return EXERCISE_URI + "/" + simulation.getId() + "/injects";
  }

  /** One agent on an endpoint of the given marking, and the COMPLETE trace it reported. */
  private record Run(MarkingDefinition marking, ExecutionTrace complete) {}

  /**
   * An atomic testing dispatched to one agent per run (none: no agent dispatched), with an optional
   * COMPLETE trace of the platform, which carries no agent.
   */
  private Inject injectWithRuns(
      InjectStatus status, List<Run> runs, ExecutionTrace platformComplete) {
    return injectWithRuns(status, runs, platformComplete, null);
  }

  /** The same, in the given simulation (none: an atomic testing). */
  private Inject injectWithRuns(
      InjectStatus status,
      List<Run> runs,
      ExecutionTrace platformComplete,
      ExerciseComposer.Composer simulation) {
    status.setExpectedAgentCount(runs.isEmpty() ? null : runs.size());
    InjectStatusComposer.Composer statusComposer = injectStatusComposer.forInjectStatus(status);
    InjectComposer.Composer injectComposerWrapper =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withInjector(InjectorFixture.createDefaultPayloadInjector()));
    List<Runnable> markings = new ArrayList<>();
    for (Run run : runs) {
      AgentComposer.Composer agent =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      EndpointComposer.Composer endpoint =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint("endpoint-" + uniqueDefinition()))
              .withAgent(agent);
      injectComposerWrapper.withEndpoint(endpoint);
      statusComposer.withExecutionTrace(completeTrace(run.complete(), agent));
      markings.add(() -> mark(endpoint.get(), run.marking()));
    }
    if (platformComplete != null) {
      statusComposer.withExecutionTrace(executionTraceComposer.forExecutionTrace(platformComplete));
    }
    injectComposerWrapper.withInjectStatus(statusComposer);
    if (simulation == null) {
      injectComposerWrapper.persist();
    } else {
      simulation.withInject(injectComposerWrapper).persist();
    }
    Inject inject = injectComposerWrapper.get();
    // Flush before the raw-JDBC marking: the rows must exist in the database for the UPDATE
    entityManager.flush();
    markings.forEach(Runnable::run);
    entityManager.clear();
    return inject;
  }

  private ExecutionTraceComposer.Composer completeTrace(
      ExecutionTrace trace, AgentComposer.Composer agent) {
    return executionTraceComposer.forExecutionTrace(trace).withAgent(agent);
  }

  private String search() throws Exception {
    return mvc.perform(
            post(ATOMIC_TESTING_SEARCH_URI)
                .content(asJsonString(PaginationFixture.getDefault().size(100).build()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String searchUnchecked() {
    try {
      return search();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String getJson(String uri) throws Exception {
    return mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String statusInPage(String page, Inject inject) {
    List<String> statuses =
        JsonPath.read(
            page,
            "$.content[?(@.inject_id == '" + inject.getId() + "')].inject_status.status_name");
    assertThat(statuses).hasSize(1);
    return statuses.getFirst();
  }

  private static String statusInList(String injects, Inject inject) {
    List<String> statuses =
        JsonPath.read(
            injects, "$[?(@.inject_id == '" + inject.getId() + "')].inject_status.status_name");
    assertThat(statuses).hasSize(1);
    return statuses.getFirst();
  }

  /**
   * The fewest statements {@code call} prepares over a few runs: the statistics count every thread
   * of the session factory, so a query of a background job can land in one run, never in all.
   */
  private long statementsOf(Supplier<String> call) {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    long fewest = Long.MAX_VALUE;
    for (int run = 0; run < STATEMENT_COUNT_RUNS; run++) {
      stats.clear();
      call.get();
      fewest = Math.min(fewest, stats.getPrepareStatementCount());
    }
    return fewest;
  }

  /** Grants markings to the group the user belongs to, and drops the cached clearance. */
  private void grant(MarkingDefinition marking) {
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        group.getId(),
        marking.getId());
    clearanceCache.evictForUser(userId);
  }

  /** Out-of-band write of the stored status, read again by the next request. */
  private void storeStatus(List<Inject> injects, String status) {
    injects.forEach(
        inject ->
            jdbc.update(
                "UPDATE injects_statuses SET status_name = ? WHERE status_inject = ?",
                status,
                inject.getId()));
    entityManager.clear();
  }

  private void mark(Endpoint endpoint, MarkingDefinition marking) {
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) new String[] {marking.getId()},
        endpoint.getId());
  }

  private MarkingDefinition marking(int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }
}
