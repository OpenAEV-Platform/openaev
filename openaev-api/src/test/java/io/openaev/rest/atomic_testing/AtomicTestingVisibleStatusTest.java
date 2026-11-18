package io.openaev.rest.atomic_testing;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
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
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject status a reader is shown, from the agents their clearance lets them see: the RED agent
 * succeeded, the GREEN one failed, so the stored verdict is PARTIAL. A GREEN reader sees only the
 * GREEN agent and reads ERROR; a RED reader sees both and reads the stored PARTIAL.
 *
 * <p>Markings are seeded with raw JDBC for the reason given in {@code AssetMarkingIsolationTest}.
 */
@Transactional
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
@DisplayName("Inject status shown from the agents the reader can see")
class AtomicTestingVisibleStatusTest extends IntegrationTest {

  private static final String ATOMIC_TESTING_SEARCH_URI = ATOMIC_TESTING_URI + "/search";
  private static final String INJECT_STATUS_URI = INJECT_URI + "/status";

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
  @Autowired private MarkingClearanceCacheManager clearanceCache;
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
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    injectStatusComposer.reset();
    executionTraceComposer.reset();
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
    @DisplayName("given an inject closed by the platform, should show the stored ERROR")
    void given_closedByPlatform_should_showTheStoredStatus() throws Exception {
      // Arrange: the RED agent succeeded, then the platform closed the inject with a trace of its
      // own
      InjectStatus status = InjectStatusFixture.createSuccessStatus();
      status.setName(ExecutionStatus.ERROR);
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
  }

  // -- HELPERS --

  /** An atomic testing on a RED and a GREEN endpoint: the RED agent succeeded, the GREEN failed. */
  private Inject partialInjectOnRedAndGreen() {
    return injectWithRuns(
        InjectStatusFixture.createPartialInjectStatus(),
        List.of(
            new Run(tlpRed, ExecutionTraceFixture.createDefaultExecutionTraceComplete()),
            new Run(tlpGreen, ExecutionTraceFixture.createDefaultExecutionTraceError())),
        null);
  }

  /** One agent on an endpoint of the given marking, and the COMPLETE trace it reported. */
  private record Run(MarkingDefinition marking, ExecutionTrace complete) {}

  /**
   * An atomic testing dispatched to one agent per run (none: no agent dispatched), with an optional
   * COMPLETE trace of the platform, which carries no agent.
   */
  private Inject injectWithRuns(
      InjectStatus status, List<Run> runs, ExecutionTrace platformComplete) {
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
    Inject inject = injectComposerWrapper.withInjectStatus(statusComposer).persist().get();
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

  private long statementsOf(Supplier<String> call) {
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    call.get();
    return stats.getPrepareStatementCount();
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
