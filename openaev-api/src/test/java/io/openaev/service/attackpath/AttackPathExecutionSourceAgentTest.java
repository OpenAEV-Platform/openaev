package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.attackpath.AttackPathExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Null-safety of {@code AttackPathExecution.setSourceAgentInformation}. The ingestion runs it for
 * every agent-sourced edge, and a run's agent or endpoint can carry null metadata: {@code
 * agent_executor} is a nullable column and {@code endpoint_platform} has no NOT NULL constraint. A
 * setter that dereferenced those blindly would throw, and since the ingestion is non-fatal the
 * whole attack-path row for that inject would be silently dropped. The row must be kept with null
 * metadata instead.
 */
@DisplayName("AttackPathExecution.setSourceAgentInformation null-safety")
class AttackPathExecutionSourceAgentTest {

  @Test
  @DisplayName("an agent with no executor and an endpoint with no platform/ips is tolerated")
  void nullExecutorAndEndpointMetadataAreTolerated() {
    Endpoint endpoint = new Endpoint(); // no platform, no ips, no hostname
    Agent agent = new Agent();
    agent.setId("agent-1");
    agent.setAsset(endpoint); // getAsset().getId() resolves; the endpoint is the agent's asset
    agent.setPrivilege(Agent.PRIVILEGE.admin); // privilege is @NotNull in the model

    AttackPathExecution e = new AttackPathExecution();
    assertThatCode(() -> e.setSourceAgentInformation(agent, endpoint)).doesNotThrowAnyException();
    assertThat(e.getAgentName()).as("no executor -> null agent name, not an NPE").isNull();
    assertThat(e.getSourcePlatform()).as("no platform -> null, not an NPE").isNull();
    assertThat(e.getSourceIp()).as("no ips -> null, not an NPE").isNull();
    assertThat(e.getAgentPrivilege()).as("privilege is set and preserved").isEqualTo("admin");
  }

  @Test
  @DisplayName("a target endpoint with no platform/ips is tolerated (same guard as the source)")
  void nullTargetEndpointMetadataIsTolerated() {
    Endpoint endpoint = new Endpoint(); // no platform, no ips
    AttackPathExecution e = new AttackPathExecution();
    assertThatCode(() -> e.setTargetAssetInformation(endpoint)).doesNotThrowAnyException();
    assertThat(e.getTargetPlatform()).as("no platform -> null, not an NPE").isNull();
    assertThat(e.getTargetIp()).as("no ips -> null, not an NPE").isNull();
  }
}
