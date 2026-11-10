package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AgentUtils Tests")
class AgentUtilsTest {

  private static Agent buildAgent(String id) {
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setId(id);
    return agent;
  }

  private static Inject buildInjectWithAgentTrace(Agent agent, ExecutionTraceStatus status) {
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    injectStatus.addTrace(
        new ExecutionTrace(
            injectStatus, status, List.of(), "test", ExecutionTraceAction.COMPLETE, agent, null));
    Inject inject = InjectFixture.getDefaultInject();
    inject.setStatus(injectStatus);
    return inject;
  }

  @Nested
  @DisplayName("Method: hasOnlyValidTraces")
  class HasOnlyValidTraces {

    @Test
    @DisplayName("Given a not targeted trace on the agent should return false")
    void given_clearanceRestrictedTraceOnAgent_should_returnFalse() {
      // -- ARRANGE --
      Agent agentRed = buildAgent("agent-red");
      Inject inject =
          buildInjectWithAgentTrace(agentRed, ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED);

      // -- ACT --
      boolean result = AgentUtils.hasOnlyValidTraces(inject, agentRed);

      // -- ASSERT --
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Given a not targeted trace on another agent should return true")
    void given_clearanceRestrictedTraceOnOtherAgent_should_returnTrue() {
      // -- ARRANGE --
      Agent agentRed = buildAgent("agent-red");
      Agent agentGreen = buildAgent("agent-green");
      Inject inject =
          buildInjectWithAgentTrace(agentRed, ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED);

      // -- ACT --
      boolean result = AgentUtils.hasOnlyValidTraces(inject, agentGreen);

      // -- ASSERT --
      assertThat(result).isTrue();
    }
  }
}
