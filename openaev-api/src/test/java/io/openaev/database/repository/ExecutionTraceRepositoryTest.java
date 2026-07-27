package io.openaev.database.repository;

import static io.openaev.utils.fixtures.AgentFixture.createDefaultAgentService;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static io.openaev.utils.fixtures.TeamFixture.getDefaultTeam;
import static io.openaev.utils.fixtures.UserFixture.getUserWithDefaultEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("ExecutionTraceRepository integration tests")
class ExecutionTraceRepositoryTest extends IntegrationTest {

  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;

  @BeforeEach
  void beforeEach() {
    injectComposer.reset();
    injectStatusComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    teamComposer.reset();
    userComposer.reset();
  }

  @Nested
  @DisplayName("findByInjectIdAndAgentId")
  class FindByInjectIdAndAgentId {

    @Test
    @DisplayName("should return traces ordered by time")
    void given_unorderedTracesForAgent_should_returnOrderedByTime() {
      // Arrange
      InjectStatus injectStatus = createInjectStatus();
      Agent agent = createAgent();

      executionTraceRepository.saveAll(
          List.of(
              createTrace(
                  injectStatus, "late", Instant.parse("2026-01-01T10:00:03Z"), agent, List.of()),
              createTrace(
                  injectStatus, "early", Instant.parse("2026-01-01T10:00:01Z"), agent, List.of()),
              createTrace(
                  injectStatus,
                  "middle",
                  Instant.parse("2026-01-01T10:00:02Z"),
                  agent,
                  List.of())));
      entityManager.flush();
      entityManager.clear();

      // Act
      List<ExecutionTrace> traces =
          executionTraceRepository.findByInjectIdAndAgentId(
              injectStatus.getInject().getId(), agent.getId());

      // Assert
      assertThat(traces)
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("early", "middle", "late");
    }
  }

  @Nested
  @DisplayName("findByInjectIdAndAssetId")
  class FindByInjectIdAndAssetId {

    @Test
    @DisplayName("should return traces ordered by time when matched by agent asset")
    void given_unorderedTracesMatchedByAgentAsset_should_returnOrderedByTime() {
      // Arrange
      InjectStatus injectStatus = createInjectStatus();
      Agent matchedAgent = createAgent();
      String endpointId = matchedAgent.getAsset().getId();

      executionTraceRepository.saveAll(
          List.of(
              createTrace(
                  injectStatus,
                  "late",
                  Instant.parse("2026-01-01T11:00:03Z"),
                  matchedAgent,
                  List.of()),
              createTrace(
                  injectStatus,
                  "early",
                  Instant.parse("2026-01-01T11:00:01Z"),
                  matchedAgent,
                  List.of()),
              createTrace(
                  injectStatus,
                  "middle",
                  Instant.parse("2026-01-01T11:00:02Z"),
                  matchedAgent,
                  List.of()),
              createTrace(
                  injectStatus,
                  "ignored",
                  Instant.parse("2026-01-01T11:00:00Z"),
                  null,
                  List.of("another-asset"))));
      entityManager.flush();
      entityManager.clear();

      // Act
      List<ExecutionTrace> traces =
          executionTraceRepository.findByInjectIdAndAssetId(
              injectStatus.getInject().getId(), endpointId);

      // Assert
      assertThat(traces)
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("early", "middle", "late");
    }

    @Test
    @DisplayName("should return traces ordered by time when matched by identifiers")
    void given_unorderedTracesMatchedByIdentifiers_should_returnOrderedByTime() {
      // Arrange
      InjectStatus injectStatus = createInjectStatus();
      String assetIdentifier = "asset-id-identifier";

      executionTraceRepository.saveAll(
          List.of(
              createTrace(
                  injectStatus,
                  "late",
                  Instant.parse("2026-01-01T12:00:03Z"),
                  null,
                  List.of(assetIdentifier)),
              createTrace(
                  injectStatus,
                  "early",
                  Instant.parse("2026-01-01T12:00:01Z"),
                  null,
                  List.of(assetIdentifier)),
              createTrace(
                  injectStatus,
                  "middle",
                  Instant.parse("2026-01-01T12:00:02Z"),
                  null,
                  List.of(assetIdentifier)),
              createTrace(
                  injectStatus,
                  "ignored",
                  Instant.parse("2026-01-01T12:00:00Z"),
                  null,
                  List.of("another-asset"))));
      entityManager.flush();
      entityManager.clear();

      // Act
      List<ExecutionTrace> traces =
          executionTraceRepository.findByInjectIdAndAssetId(
              injectStatus.getInject().getId(), assetIdentifier);

      // Assert
      assertThat(traces)
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("early", "middle", "late");
    }
  }

  @Nested
  @DisplayName("findByInjectIdAndTeamId")
  class FindByInjectIdAndTeamId {

    @Test
    @DisplayName("should return traces ordered by time")
    void given_unorderedTracesForTeamUserIdentifier_should_returnOrderedByTime() {
      // Arrange
      InjectStatus injectStatus = createInjectStatus();

      var user = userComposer.forUser(getUserWithDefaultEmail()).persist().get();
      var team =
          teamComposer
              .forTeam(getDefaultTeam())
              .withUser(userComposer.forUser(user))
              .persist()
              .get();

      executionTraceRepository.saveAll(
          List.of(
              createTrace(
                  injectStatus,
                  "late",
                  Instant.parse("2026-01-01T13:00:03Z"),
                  null,
                  List.of(user.getId())),
              createTrace(
                  injectStatus,
                  "early",
                  Instant.parse("2026-01-01T13:00:01Z"),
                  null,
                  List.of(user.getId())),
              createTrace(
                  injectStatus,
                  "middle",
                  Instant.parse("2026-01-01T13:00:02Z"),
                  null,
                  List.of(user.getId())),
              createTrace(
                  injectStatus,
                  "ignored",
                  Instant.parse("2026-01-01T13:00:00Z"),
                  null,
                  List.of("another-user"))));
      entityManager.flush();
      entityManager.clear();

      // Act
      List<ExecutionTrace> traces =
          executionTraceRepository.findByInjectIdAndTeamId(
              injectStatus.getInject().getId(), team.getId());

      // Assert
      assertThat(traces)
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("early", "middle", "late");
    }
  }

  @Nested
  @DisplayName("findByInjectIdAndPlayerId")
  class FindByInjectIdAndPlayerId {

    @Test
    @DisplayName("should return traces ordered by time")
    void given_unorderedTracesForPlayerIdentifier_should_returnOrderedByTime() {
      // Arrange
      InjectStatus injectStatus = createInjectStatus();
      String playerId = "player-123";

      executionTraceRepository.saveAll(
          List.of(
              createTrace(
                  injectStatus,
                  "late",
                  Instant.parse("2026-01-01T14:00:03Z"),
                  null,
                  List.of(playerId)),
              createTrace(
                  injectStatus,
                  "early",
                  Instant.parse("2026-01-01T14:00:01Z"),
                  null,
                  List.of(playerId)),
              createTrace(
                  injectStatus,
                  "middle",
                  Instant.parse("2026-01-01T14:00:02Z"),
                  null,
                  List.of(playerId)),
              createTrace(
                  injectStatus,
                  "ignored",
                  Instant.parse("2026-01-01T14:00:00Z"),
                  null,
                  List.of("another-player"))));
      entityManager.flush();
      entityManager.clear();

      // Act
      List<ExecutionTrace> traces =
          executionTraceRepository.findByInjectIdAndPlayerId(
              injectStatus.getInject().getId(), playerId);

      // Assert
      assertThat(traces)
          .extracting(ExecutionTrace::getMessage)
          .containsExactly("early", "middle", "late");
    }
  }

  private InjectStatus createInjectStatus() {
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .persist()
            .get();
    return inject.getStatus().orElseThrow();
  }

  private Agent createAgent() {
    var endpoint =
        endpointComposer
            .forEndpoint(createEndpoint())
            .withAgent(agentComposer.forAgent(createDefaultAgentService()))
            .persist()
            .get();
    return endpoint.getAgents().get(0);
  }

  private ExecutionTrace createTrace(
      InjectStatus status, String message, Instant time, Agent agent, List<String> identifiers) {
    return new ExecutionTrace(
        status,
        ExecutionTraceStatus.INFO,
        identifiers,
        message,
        ExecutionTraceAction.START,
        agent,
        time);
  }
}
