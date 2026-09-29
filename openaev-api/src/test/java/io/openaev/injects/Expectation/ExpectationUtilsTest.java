package io.openaev.injects.Expectation;

import static io.openaev.utils.ExpectationUtils.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.utils.fixtures.*;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpectationUtilsTest extends IntegrationTest {

  private Inject injectWithInjector(Injector injector) {
    Inject inject = new Inject();
    inject.setInjector(injector);
    return inject;
  }

  @Test
  @DisplayName("Endpoint targeted by a payload injector needs no agentless expectation")
  void given_endpointWithPayloadInjector_should_notNeedAgentlessExpectation() {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setAgents(List.of());
    Inject inject = injectWithInjector(InjectorFixture.createDefaultPayloadInjector());

    assertFalse(isAgentlessAssetExpectationNecessary(endpoint, inject));
  }

  @Test
  @DisplayName(
      "Endpoint with no agent targeted by a non-payload injector needs an agentless expectation")
  void given_endpointWithoutAgentAndNonPayloadInjector_should_needAgentlessExpectation() {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setAgents(List.of());
    Inject inject =
        injectWithInjector(InjectorFixture.createInjector("injectorId", "manual", "manual"));

    assertTrue(isAgentlessAssetExpectationNecessary(endpoint, inject));
  }

  @Test
  @DisplayName("Endpoint carrying an agent needs no agentless expectation")
  void given_endpointWithAgentAndNonPayloadInjector_should_notNeedAgentlessExpectation() {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createAgent(endpoint, "ext");
    agent.setId("agentId");
    endpoint.setAgents(List.of(agent));
    Inject inject =
        injectWithInjector(InjectorFixture.createInjector("injectorId", "manual", "manual"));

    assertFalse(isAgentlessAssetExpectationNecessary(endpoint, inject));
  }

  @Test
  @DisplayName(
      "Endpoint whose only agent is inactive still needs an agentless expectation for a non-payload"
          + " injector")
  void given_endpointWithOnlyInactiveAgentAndNonPayloadInjector_should_needAgentlessExpectation() {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    // A network scanner (Nuclei, Nmap...) reaches the endpoint regardless of agent health: a dead
    // agent must not swallow the asset-level expectation (the endpoint would otherwise get no
    // expectation at all - no active agent children, no agentless parent).
    Agent inactiveAgent = AgentFixture.createInactiveAgent();
    inactiveAgent.setId("inactiveAgentId");
    inactiveAgent.setAsset(endpoint);
    endpoint.setAgents(List.of(inactiveAgent));
    Inject inject =
        injectWithInjector(InjectorFixture.createInjector("injectorId", "manual", "manual"));

    assertTrue(isAgentlessAssetExpectationNecessary(endpoint, inject));
  }

  @Test
  @DisplayName("AI target asset always needs an agentless expectation, even for a payload injector")
  void given_aiTargetAssetWithPayloadInjector_should_needAgentlessExpectation() {
    Asset aiTarget = new Asset();
    aiTarget.setId("aiTargetId");
    aiTarget.setName("LLM firewall target");
    aiTarget.setCategory(AssetCategory.AI_TARGET);
    // The ai-redteam injector is payload-based, yet an AI target carries no agent: the asset
    // itself is the validation target, so it must still get an asset-level expectation.
    Inject inject = injectWithInjector(InjectorFixture.createDefaultPayloadInjector());

    assertTrue(isAgentlessAssetExpectationNecessary(aiTarget, inject));
  }

  @Test
  @DisplayName("A null asset never needs an agentless expectation")
  void given_nullAsset_should_notNeedAgentlessExpectation() {
    Inject inject = injectWithInjector(InjectorFixture.createDefaultPayloadInjector());

    assertFalse(isAgentlessAssetExpectationNecessary(null, inject));
  }
}
