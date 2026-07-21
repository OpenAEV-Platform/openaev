package io.openaev.injects.Expectation;

import static io.openaev.utils.ExpectationSignatureUtils.*;
import static io.openaev.utils.ExpectationUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.expectation.DetectionExpectation;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.expectation.PreventionExpectation;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.utils.fixtures.*;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpectationUtilsTest extends IntegrationTest {

  @Test
  @DisplayName("Build expectations with the signature parent process name for oaev implant")
  void shouldBuildExpectationsWithSignatureParentProcessNameForOaevImplant_Prevention() {
    // -- PREPARE --
    Endpoint endpoint = EndpointFixture.createEndpoint();
    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    Inject inject = InjectFixture.createTechnicalInject(injectorContract, "Inject", endpoint);
    inject.setId("injectId");

    Agent agent = AgentFixture.createAgent(endpoint, "ext");
    agent.setId("agentId");
    endpoint.setAgents(List.of(agent));

    AssetToExecute assetToExecute = new AssetToExecute(endpoint, true, List.of());

    // -- EXECUTE --
    List<PreventionExpectation> preventionExpectations =
        getPreventionExpectationsByAsset(
            OAEV_IMPLANT,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            new HashMap<>(),
            inject);

    List<DetectionExpectation> detectionExpectations =
        getDetectionExpectationsByAsset(
            OAEV_IMPLANT,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            new HashMap<>(),
            inject);

    // -- ASSERT --
    ExpectationSignature signature =
        new ExpectationSignature(
            EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME, "oaev-implant-injectId-agent-agentId");

    assertEquals(2, preventionExpectations.size());
    assertEquals(2, detectionExpectations.size());

    assertEquals(PreventionExpectation.class, preventionExpectations.getFirst().getClass());
    assertEquals(DetectionExpectation.class, detectionExpectations.getFirst().getClass());

    assertEquals(
        signature,
        preventionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(prev -> prev.getExpectationSignatures().stream())
            .toList()
            .getFirst());

    assertEquals(
        signature,
        detectionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(det -> det.getExpectationSignatures().stream())
            .toList()
            .getFirst());
  }

  @Test
  @DisplayName("Build expectations with the signature parent process name for caldera implant")
  void shouldBuildExpectationsWithSignatureParentProcessNameForCalderaImplant() {
    // -- PREPARE --
    Endpoint endpoint = EndpointFixture.createEndpoint();
    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    Inject inject = InjectFixture.createTechnicalInject(injectorContract, "Inject", endpoint);
    inject.setId("injectId");

    Agent agentParent = AgentFixture.createAgent(endpoint, "ext-parent");
    agentParent.setId("agentParentId");
    agentParent.setInject(inject);
    Agent agent = AgentFixture.createAgent(endpoint, "ext");
    agent.setId("agentId");
    agent.setInject(inject);
    agent.setParent(agentParent);

    AssetToExecute assetToExecute = new AssetToExecute(endpoint, true, List.of());

    // -- EXECUTE --

    List<PreventionExpectation> preventionExpectations =
        getPreventionExpectationsByAsset(
            OAEV_IMPLANT_CALDERA,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            new HashMap<>(),
            null);

    List<DetectionExpectation> detectionExpectations =
        getDetectionExpectationsByAsset(
            OAEV_IMPLANT_CALDERA,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            new HashMap<>(),
            null);

    // -- ASSERT --
    ExpectationSignature signature =
        new ExpectationSignature(
            EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME,
            "oaev-implant-caldera-injectId-agent-agentParentId");

    assertEquals(2, preventionExpectations.size());
    assertEquals(2, detectionExpectations.size());

    assertEquals(PreventionExpectation.class, preventionExpectations.getFirst().getClass());
    assertEquals(DetectionExpectation.class, detectionExpectations.getFirst().getClass());

    assertEquals(
        signature,
        preventionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(prev -> prev.getExpectationSignatures().stream())
            .toList()
            .getFirst());

    assertEquals(
        signature,
        detectionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(det -> det.getExpectationSignatures().stream())
            .toList()
            .getFirst());
  }

  @Test
  @DisplayName("Should build expectations with the source ip signature and target ip signature")
  void given_assetSource_should_buildSourceIPSignature() {
    String[] fakeIPs = {"192.168.1.1", "192.168.1.2"};
    String fakeSeenIPV6 = "9121:ea03:3ff4:d76e:2f68:ff93:a462:7d27";
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setIps(fakeIPs);
    endpoint.setSeenIp(fakeSeenIPV6);

    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    Inject inject = InjectFixture.createTechnicalInject(injectorContract, "Inject", endpoint);
    inject.setId("injectId");

    Agent agent = AgentFixture.createAgent(endpoint, "ext");
    agent.setId("agentId");
    endpoint.setAgents(List.of(agent));

    String targetHostname = "http://target";
    String target2Ip = "100.90.200.90";
    Endpoint targetEndpoint = EndpointFixture.createEndpoint();
    targetEndpoint.setHostname(targetHostname);
    Endpoint targetEndpoint2 = EndpointFixture.createEndpoint();
    targetEndpoint2.setSeenIp(target2Ip);
    Map<String, Endpoint> targetValues = new HashMap<>();
    targetValues.put(targetHostname, targetEndpoint);
    targetValues.put(target2Ip, targetEndpoint2);

    AssetToExecute assetToExecute = new AssetToExecute(endpoint, true, List.of());

    // -- EXECUTE --
    List<PreventionExpectation> preventionExpectations =
        getPreventionExpectationsByAsset(
            OAEV_IMPLANT,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            targetValues,
            inject);

    List<String> preventionSourceIpv4SignatureValues = new ArrayList<>();
    List<String> preventionSourceIpv6SignatureValues = new ArrayList<>();

    List<String> preventionTargetIpv4SignatureValues = new ArrayList<>();
    List<String> preventionTargetHostnamesSignatureValues = new ArrayList<>();

    preventionExpectations.stream()
        .filter(expectation -> expectation.getAgent() != null)
        .toList()
        .getFirst()
        .getExpectationSignatures()
        .forEach(
            signature -> {
              switch (signature.getType()) {
                case EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV4_ADDRESS ->
                    preventionSourceIpv4SignatureValues.add(signature.getValue());
                case EXPECTATION_SIGNATURE_TYPE_SOURCE_IPV6_ADDRESS ->
                    preventionSourceIpv6SignatureValues.add(signature.getValue());
                case EXPECTATION_SIGNATURE_TYPE_TARGET_IPV4_ADDRESS ->
                    preventionTargetIpv4SignatureValues.add(signature.getValue());
                case EXPECTATION_SIGNATURE_TYPE_TARGET_HOSTNAME_ADDRESS ->
                    preventionTargetHostnamesSignatureValues.add(signature.getValue());
                case EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME -> {
                  /* intentionally ignored */
                }
                default ->
                    throw new IllegalArgumentException(
                        "Invalid signature type: " + signature.getType());
              }
            });

    assertEquals(2, preventionSourceIpv4SignatureValues.size());
    assertEquals(1, preventionSourceIpv6SignatureValues.size());
    assertTrue(preventionSourceIpv4SignatureValues.containsAll(Arrays.asList(fakeIPs)));
    assertEquals(fakeSeenIPV6, preventionSourceIpv6SignatureValues.getFirst());

    assertEquals(1, preventionTargetIpv4SignatureValues.size());
    assertEquals(1, preventionTargetHostnamesSignatureValues.size());
    assertEquals(targetHostname, preventionTargetHostnamesSignatureValues.getFirst());
    assertEquals(target2Ip, preventionTargetIpv4SignatureValues.getFirst());
  }

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
