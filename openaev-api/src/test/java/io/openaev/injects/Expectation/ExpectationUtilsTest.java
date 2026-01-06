package io.openaev.injects.Expectation;

import static io.openaev.database.model.InjectExpectationSignature.*;
import static io.openaev.utils.ExpectationUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.PreventionExpectation;
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
            inject.getId());

    List<DetectionExpectation> detectionExpectations =
        getDetectionExpectationsByAsset(
            OAEV_IMPLANT,
            assetToExecute,
            List.of(agent),
            ExpectationFixture.createExpectation(),
            new HashMap<>(),
            inject.getId());

    // -- ASSERT --
    InjectExpectationSignature signature =
        InjectExpectationSignature.builder()
            .type(EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME)
            .value("oaev-implant-injectId-agent-agentId")
            .build();

    assertEquals(2, preventionExpectations.size());
    assertEquals(2, detectionExpectations.size());

    assertEquals(PreventionExpectation.class, preventionExpectations.getFirst().getClass());
    assertEquals(DetectionExpectation.class, detectionExpectations.getFirst().getClass());

    assertEquals(
        signature,
        preventionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(prev -> prev.getInjectExpectationSignatures().stream())
            .toList()
            .getFirst());

    assertEquals(
        signature,
        detectionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(det -> det.getInjectExpectationSignatures().stream())
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
    InjectExpectationSignature signature =
        InjectExpectationSignature.builder()
            .type(EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME)
            .value("oaev-implant-caldera-injectId-agent-agentParentId")
            .build();

    assertEquals(2, preventionExpectations.size());
    assertEquals(2, detectionExpectations.size());

    assertEquals(PreventionExpectation.class, preventionExpectations.getFirst().getClass());
    assertEquals(DetectionExpectation.class, detectionExpectations.getFirst().getClass());

    assertEquals(
        signature,
        preventionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(prev -> prev.getInjectExpectationSignatures().stream())
            .toList()
            .getFirst());

    assertEquals(
        signature,
        detectionExpectations.stream()
            .filter(expectation -> expectation.getAgent() != null)
            .flatMap(det -> det.getInjectExpectationSignatures().stream())
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
            inject.getId());

    List<String> preventionSourceIpv4SignatureValues = new ArrayList<>();
    List<String> preventionSourceIpv6SignatureValues = new ArrayList<>();

    List<String> preventionTargetIpv4SignatureValues = new ArrayList<>();
    List<String> preventionTargetHostnamesSignatureValues = new ArrayList<>();

    preventionExpectations.stream()
        .filter(expectation -> expectation.getAgent() != null)
        .toList()
        .getFirst()
        .getInjectExpectationSignatures()
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
}
