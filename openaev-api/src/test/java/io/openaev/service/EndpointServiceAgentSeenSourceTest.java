package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Executor;
import io.openaev.executors.model.AgentRegisterInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EndpointService#agentSeenSource: the source an agent observation is stamped with")
class EndpointServiceAgentSeenSourceTest {

  @Test
  @DisplayName("given an executor with a type should stamp agent and that type")
  void given_executorWithType_should_stampItsType() {
    // Arrange
    Executor executor = new Executor();
    executor.setType("openaev_agent");
    AgentRegisterInput input = new AgentRegisterInput();
    input.setExecutor(executor);

    // Act + Assert
    assertThat(EndpointService.agentSeenSource(input)).isEqualTo("agent:openaev_agent");
  }

  @Test
  @DisplayName("given an executor without a type should stamp agent:unknown")
  void given_executorWithoutType_should_stampUnknown() {
    // Arrange
    AgentRegisterInput input = new AgentRegisterInput();
    input.setExecutor(new Executor());

    // Act + Assert
    assertThat(EndpointService.agentSeenSource(input)).isEqualTo("agent:unknown");
  }

  @Test
  @DisplayName("given no executor should stamp agent:unknown")
  void given_noExecutor_should_stampUnknown() {
    assertThat(EndpointService.agentSeenSource(new AgentRegisterInput()))
        .isEqualTo("agent:unknown");
  }
}
