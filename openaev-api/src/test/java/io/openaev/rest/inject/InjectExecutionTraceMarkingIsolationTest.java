package io.openaev.rest.inject;

import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The traces written for targets outside the launcher's clearance ({@code
 * ASSET_CLEARANCE_RESTRICTED}) must not reveal those targets to a reader who lacks that clearance,
 * on any of the inject trace endpoints.
 *
 * <p>The scenario is the PR test plan: user B, cleared {@code TLP:GREEN}, launches an inject on a
 * {@code TLP:GREEN} and a {@code TLP:RED} target. The {@code TLP:RED} targets get a "not targeted"
 * trace, written here by the real {@link ExecutionExecutorService} methods. B must not see them;
 * user A, cleared {@code TLP:RED}, must.
 *
 * <p>Seeding and marking follow {@code AssetMarkingIsolationTest}: rows are created unmarked, then
 * marked through raw JDBC, which bypasses the statement inspector. The persistence context is
 * cleared afterwards so the endpoints reload the traces through SQL, where the marking filter
 * applies, instead of reading the collection the seeding left in memory.
 *
 * <p><b>One HTTP call per test.</b> The marking scope is written once per transaction and the test
 * transaction wraps every request, so a second call in the same test would reuse the first one's
 * clearance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
@DisplayName("Inject execution traces marking isolation through the real HTTP endpoints")
class InjectExecutionTraceMarkingIsolationTest extends IntegrationTest {

  private static final String ALL_TRACES_URI = INJECT_URI + "/status-all-traces";
  private static final String TARGET_TRACES_URI = INJECT_URI + "/execution-traces";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutionExecutorService executionExecutorService;

  private JdbcTemplate jdbc;
  private String userId;
  private Group group;

  private MarkingDefinition tlpGreen;
  private MarkingDefinition tlpRed;

  private Inject inject;
  private Agent greenAgent;
  private Agent redAgent;
  private Endpoint redAgentlessEndpoint;

  @BeforeEach
  void seed() {
    // Arrange
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    executionTraceComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();

    String tenantId = TenantContext.getCurrentTenant();
    User user = testUserHolder.get();
    userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    tlpGreen = marking(tenantId, 60);
    tlpRed = marking(tenantId, 70);

    AgentComposer.Composer greenAgentComposer = agentWithEndpoint();
    AgentComposer.Composer redAgentComposer = agentWithEndpoint();
    greenAgent = greenAgentComposer.get();
    redAgent = redAgentComposer.get();
    redAgentlessEndpoint =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint("trace-iso-red-agentless"))
            .persist()
            .get();

    // The GREEN agent was targeted normally: its trace is the control proving B still sees
    // something, so that "nothing returned" cannot pass for "restricted traces hidden".
    inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer
                    .forInjectStatus(InjectStatusFixture.createPendingInjectStatus())
                    .withExecutionTrace(
                        executionTraceComposer
                            .forExecutionTrace(
                                ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                            .withAgent(greenAgentComposer)))
            .persist()
            .get();
    InjectStatus injectStatus = inject.getStatus().orElseThrow();
    executionExecutorService.saveAgentsWithoutClearanceTraces(Set.of(redAgent), injectStatus);
    executionExecutorService.saveAssetsWithoutClearanceTraces(
        Set.of(redAgentlessEndpoint), injectStatus);

    group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("trace-iso-" + uniqueDefinition()))
            .persist()
            .get();
    group.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(group);

    // The rows must exist in the database for the raw-JDBC marking below to find them.
    entityManager.flush();

    mark(greenAgent.getAsset(), tlpGreen);
    mark(redAgent.getAsset(), tlpRed);
    mark(redAgentlessEndpoint, tlpRed);

    entityManager.clear();
  }

  @Nested
  @DisplayName("as user B, cleared TLP:GREEN")
  class GreenCleared {

    @BeforeEach
    void grantGreen() {
      grant(tlpGreen);
    }

    @Test
    @DisplayName(
        "given the full execution log, should hide the not-targeted trace of a TLP:RED agent")
    void given_allTraces_should_hideRestrictedAgentTrace() throws Exception {
      // Act
      String response = perform(get(ALL_TRACES_URI).param("injectId", inject.getId()));

      // Assert
      List<String> agentIds =
          JsonPath.read(response, "$.status_main_traces[*].execution_agent.agent_id");
      assertTrue(
          agentIds.contains(greenAgent.getId()), "the TLP:GREEN agent trace must stay visible");
      assertFalse(agentIds.contains(redAgent.getId()), "the TLP:RED agent must not be disclosed");
    }

    @Test
    @DisplayName(
        "given the full execution log, should hide the not-targeted trace of a TLP:RED agentless"
            + " asset")
    void given_allTraces_should_hideRestrictedAgentlessAssetTrace() throws Exception {
      // Act
      String response = perform(get(ALL_TRACES_URI).param("injectId", inject.getId()));

      // Assert
      List<String> statuses = JsonPath.read(response, "$.status_main_traces[*].execution_status");
      assertTrue(
          statuses.contains(ExecutionTraceStatus.EXECUTED.name()),
          "the TLP:GREEN agent trace must stay visible");
      // Even without the asset id in the output, one line per hidden asset discloses that it
      // exists and how many of them were targeted.
      assertFalse(
          statuses.contains(ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED.name()),
          "no not-targeted trace may reach a reader without the TLP:RED clearance");
    }

    @Test
    @DisplayName("given the traces of a TLP:RED agent, should return nothing")
    void given_restrictedAgentTarget_should_returnNoTrace() throws Exception {
      // Act
      String response = performTargetTraces(redAgent.getId(), TargetType.AGENT);

      // Assert
      assertEquals(List.of(), JsonPath.read(response, "$[*]"));
    }

    @Test
    @DisplayName("given the traces of a TLP:RED agentless asset, should return nothing")
    void given_restrictedAgentlessAssetTarget_should_returnNoTrace() throws Exception {
      // Act
      String response = performTargetTraces(redAgentlessEndpoint.getId(), TargetType.ASSETS);

      // Assert
      assertEquals(List.of(), JsonPath.read(response, "$[*]"));
    }
  }

  @Nested
  @DisplayName("as user A, cleared TLP:RED")
  class RedCleared {

    @BeforeEach
    void grantRed() {
      grant(tlpRed);
    }

    @Test
    @DisplayName("given the full execution log, should show every not-targeted trace")
    void given_allTraces_should_showEveryNotTargetedTrace() throws Exception {
      // Act
      String response = perform(get(ALL_TRACES_URI).param("injectId", inject.getId()));

      // Assert
      // The negative cases above mean nothing without this one: it rules out "never written".
      List<String> statuses = JsonPath.read(response, "$.status_main_traces[*].execution_status");
      assertEquals(
          2,
          statuses.stream()
              .filter(ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED.name()::equals)
              .count(),
          "the TLP:RED agent and the TLP:RED agentless asset must each have a trace");
      List<String> agentIds =
          JsonPath.read(response, "$.status_main_traces[*].execution_agent.agent_id");
      assertTrue(agentIds.contains(redAgent.getId()));
      assertTrue(agentIds.contains(greenAgent.getId()), "RED clearance must imply GREEN");
    }
  }

  // -- HELPERS --

  private String perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String performTargetTraces(String targetId, TargetType targetType) throws Exception {
    return perform(
        get(TARGET_TRACES_URI)
            .param("injectId", inject.getId())
            .param("targetId", targetId)
            .param("targetType", targetType.name()));
  }

  private AgentComposer.Composer agentWithEndpoint() {
    AgentComposer.Composer agent = agentComposer.forAgent(AgentFixture.createDefaultAgentService());
    endpointComposer
        .forEndpoint(EndpointFixture.createEndpoint("trace-iso-" + uniqueDefinition()))
        .withAgent(agent)
        .persist();
    return agent;
  }

  /** Grants markings to the group the user belongs to, and drops the cached clearance. */
  private void grant(MarkingDefinition... markings) {
    for (MarkingDefinition marking : markings) {
      jdbc.update(
          "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)"
              + " ON CONFLICT DO NOTHING",
          group.getId(),
          marking.getId());
    }
    clearanceCache.evictForUser(userId);
  }

  /** Out-of-band write: see {@code AssetMarkingIsolationTest} for why this is not an ORM save. */
  private void mark(Asset asset, MarkingDefinition... markings) {
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) Arrays.stream(markings).map(MarkingDefinition::getId).toArray(String[]::new),
        asset.getId());
  }

  private MarkingDefinition marking(String tenantId, int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }
}
