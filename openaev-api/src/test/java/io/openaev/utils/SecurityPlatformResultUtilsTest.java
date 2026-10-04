package io.openaev.utils;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.PENDING;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createCollectorResult;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createManualResult;
import static io.openaev.utils.fixtures.InjectExpectationResultFixture.createSecurityPlatformResult;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.expectation.ExpectationType;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SecurityPlatformResultUtils")
class SecurityPlatformResultUtilsTest {

  private static SecurityPlatform createPlatform(String name, String type) {
    SecurityPlatform platform = SecurityPlatformFixture.createDefault(name, type);
    platform.setId(UUID.randomUUID().toString());
    return platform;
  }

  private static BaseInjectExpectation createExpectation(
      BaseInjectExpectation.EXPECTATION_TYPE type,
      BaseInjectExpectation.EXPECTATION_STATUS globalStatus,
      InjectExpectationResult... results) {
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(type, globalStatus);
    expectation.setResults(new ArrayList<>(List.of(results)));
    return expectation;
  }

  private static ExpectationResultsByType resultOfType(
      List<ExpectationResultsByType> results, ExpectationType type) {
    return results.stream().filter(result -> result.type() == type).findFirst().orElseThrow();
  }

  @Nested
  @DisplayName("Method: isFromSecurityPlatform")
  class IsFromSecurityPlatform {

    @Test
    @DisplayName("A collector result is attributed to the platform the collector is linked to")
    void given_collectorResultLinkedToPlatform_should_beAttributedToThePlatform() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      InjectExpectationResult result = createCollectorResult(edr, 100.0);

      // Act
      boolean attributed = SecurityPlatformResultUtils.isFromSecurityPlatform(result, edr.getId());

      // Assert
      assertThat(result.getSourceId()).isNotEqualTo(edr.getId());
      assertThat(attributed).isTrue();
    }

    @Test
    @DisplayName("A result written by the platform itself is attributed to it")
    void given_resultWrittenByThePlatform_should_beAttributedToThePlatform() {
      // Arrange
      SecurityPlatform scanner = createPlatform("Scanner", "ISPM");
      InjectExpectationResult result = createSecurityPlatformResult(scanner, 100.0);

      // Act
      boolean attributed =
          SecurityPlatformResultUtils.isFromSecurityPlatform(result, scanner.getId());

      // Assert
      assertThat(attributed).isTrue();
    }

    @Test
    @DisplayName("Results of other sources, or no result at all, are not attributed")
    void given_resultOfAnotherSource_should_notBeAttributed() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform siem = createPlatform("SIEM", "SIEM");

      // Act + Assert
      assertThat(
              SecurityPlatformResultUtils.isFromSecurityPlatform(
                  createCollectorResult(siem, 100.0), edr.getId()))
          .isFalse();
      assertThat(
              SecurityPlatformResultUtils.isFromSecurityPlatform(
                  createManualResult(100.0), edr.getId()))
          .isFalse();
      assertThat(SecurityPlatformResultUtils.isFromSecurityPlatform(null, edr.getId())).isFalse();
    }
  }

  @Nested
  @DisplayName("Method: toSecurityPlatformView")
  class ToSecurityPlatformView {

    @Test
    @DisplayName("Keeps only the platform results, scored with the best one, on a detached copy")
    void given_resultsOfSeveralSources_should_keepOnlyThePlatformResultsScoredWithTheBest() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform siem = createPlatform("SIEM", "SIEM");
      BaseInjectExpectation expectation =
          createExpectation(
              DETECTION,
              SUCCESS,
              createCollectorResult(edr, 0.0),
              createSecurityPlatformResult(edr, 100.0),
              createCollectorResult(siem, 100.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(expectation, edr.getId());

      // Assert
      assertThat(view).isNotSameAs(expectation);
      assertThat(view.getResults())
          .hasSize(2)
          .allMatch(r -> edr.getId().equals(r.getSourceAssetId()));
      assertThat(view.getScore()).isEqualTo(100.0);
      assertThat(expectation.getResults()).hasSize(3);
      assertThat(expectation.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("A platform that has not answered yet leaves the expectation pending")
    void given_onlyPendingPlatformResults_should_scoreTheViewAsPending() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      BaseInjectExpectation expectation =
          createExpectation(DETECTION, SUCCESS, createCollectorResult(edr, null));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(expectation, edr.getId());

      // Assert
      assertThat(view.getResults()).hasSize(1);
      assertThat(view.getScore()).isNull();
    }
  }

  @Nested
  @DisplayName("Collector results on agent expectations roll up to their parents")
  class AgentResultsRollUp {

    private final Inject inject = InjectFixture.getDefaultInject();

    private Endpoint createEndpointWithAgents(int agentCount) {
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId(UUID.randomUUID().toString());
      List<Agent> agents = new ArrayList<>();
      for (int i = 0; i < agentCount; i++) {
        Agent agent = AgentFixture.createDefaultAgentService();
        agent.setId(UUID.randomUUID().toString());
        agent.setAsset(endpoint);
        agents.add(agent);
      }
      endpoint.setAgents(agents);
      return endpoint;
    }

    private TechnicalInjectExpectation addTechnicalExpectation(
        AssetGroup assetGroup,
        Endpoint asset,
        Agent agent,
        BaseInjectExpectation.EXPECTATION_STATUS globalStatus,
        InjectExpectationResult... results) {
      return addTechnicalExpectation(DETECTION, assetGroup, asset, agent, globalStatus, results);
    }

    private TechnicalInjectExpectation addTechnicalExpectation(
        BaseInjectExpectation.EXPECTATION_TYPE type,
        AssetGroup assetGroup,
        Endpoint asset,
        Agent agent,
        BaseInjectExpectation.EXPECTATION_STATUS globalStatus,
        InjectExpectationResult... results) {
      TechnicalInjectExpectation expectation =
          (TechnicalInjectExpectation) createExpectation(type, globalStatus, results);
      expectation.setInject(inject);
      expectation.setAssetGroup(assetGroup);
      expectation.setAsset(asset);
      expectation.setAgent(agent);
      inject.getExpectations().add(expectation);
      return expectation;
    }

    @Test
    @DisplayName("An asset whose agents all detected is detected for the platform")
    void given_platformResultsOnEveryAgent_should_scoreTheAssetExpectationForThePlatform() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Endpoint endpoint = createEndpointWithAgents(2);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS);
      endpoint
          .getAgents()
          .forEach(
              agent ->
                  addTechnicalExpectation(
                      null, endpoint, agent, SUCCESS, createCollectorResult(edr, 100.0)));

      // Act
      boolean reported =
          SecurityPlatformResultUtils.hasResultFromSecurityPlatform(assetExpectation, edr.getId());
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, edr.getId());
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              List.of(assetExpectation), Set.of(edr.getId()));

      // Assert
      assertThat(assetExpectation.getResults()).isEmpty();
      assertThat(reported).isTrue();
      assertThat(view.getScore()).isEqualTo(100.0);
      assertThat(
              resultOfType(resultsByPlatform.get(edr.getId()), ExpectationType.DETECTION)
                  .getSuccessRate())
          .isEqualTo(1.0);
    }

    @Test
    @DisplayName("An asset with one undetected agent is not detected for the platform")
    void given_oneAgentNotDetected_should_failTheAssetExpectationForThePlatform() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Endpoint endpoint = createEndpointWithAgents(2);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS);
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(0), SUCCESS, createCollectorResult(edr, 100.0));
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(1), SUCCESS, createCollectorResult(edr, 0.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, edr.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("A direct detection on the asset wins over an undetected agent, like the score")
    void given_directDetectionAndUndetectedAgent_should_keepTheDirectDetection() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS, createCollectorResult(edr, 100.0));
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(0), SUCCESS, createCollectorResult(edr, 0.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, edr.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("A direct miss on the asset defers to the agents, like the score")
    void given_directMissAndDetectedAgent_should_deferToTheAgents() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS, createCollectorResult(edr, 0.0));
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(0), SUCCESS, createCollectorResult(edr, 100.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, edr.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("A vulnerable agent wins over a direct not vulnerable verdict on the asset")
    void given_directNotVulnerableAndVulnerableAgent_should_keepTheVulnerableVerdict() {
      // Arrange
      SecurityPlatform scanner = createPlatform("Scanner", "SIEM");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(
              VULNERABILITY, null, endpoint, null, SUCCESS, createCollectorResult(scanner, 100.0));
      addTechnicalExpectation(
          VULNERABILITY,
          null,
          endpoint,
          endpoint.getAgents().get(0),
          SUCCESS,
          createCollectorResult(scanner, 0.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, scanner.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("A direct vulnerable verdict on the asset wins over not vulnerable agents")
    void given_directVulnerableAndNotVulnerableAgent_should_keepTheVulnerableVerdict() {
      // Arrange
      SecurityPlatform scanner = createPlatform("Scanner", "SIEM");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(
              VULNERABILITY, null, endpoint, null, SUCCESS, createCollectorResult(scanner, 0.0));
      addTechnicalExpectation(
          VULNERABILITY,
          null,
          endpoint,
          endpoint.getAgents().get(0),
          SUCCESS,
          createCollectorResult(scanner, 100.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, scanner.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("A direct not vulnerable verdict on the asset holds with not vulnerable agents")
    void given_directNotVulnerableAndNotVulnerableAgent_should_keepTheDirectVerdict() {
      // Arrange
      SecurityPlatform scanner = createPlatform("Scanner", "SIEM");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(
              VULNERABILITY, null, endpoint, null, SUCCESS, createCollectorResult(scanner, 100.0));
      addTechnicalExpectation(
          VULNERABILITY,
          null,
          endpoint,
          endpoint.getAgents().get(0),
          SUCCESS,
          createCollectorResult(scanner, 100.0));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, scanner.getId());

      // Assert
      assertThat(view.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("An asset with an agent the platform has not answered yet stays pending")
    void given_agentStillPendingForThePlatform_should_keepTheAssetExpectationPending() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      Endpoint endpoint = createEndpointWithAgents(2);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS);
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(0), SUCCESS, createCollectorResult(edr, 100.0));
      addTechnicalExpectation(
          null, endpoint, endpoint.getAgents().get(1), PENDING, createCollectorResult(edr, null));

      // Act
      BaseInjectExpectation view =
          SecurityPlatformResultUtils.toSecurityPlatformView(assetExpectation, edr.getId());

      // Assert
      assertThat(view.getScore()).isNull();
    }

    @Test
    @DisplayName("An asset group follows its validation mode for the platform")
    void given_assetGroup_should_rollUpThePlatformVerdictsOfItsAssets() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("Workstations");
      assetGroup.setId(UUID.randomUUID().toString());
      Endpoint detectedEndpoint = createEndpointWithAgents(1);
      Endpoint missedEndpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation groupExpectation =
          addTechnicalExpectation(assetGroup, null, null, SUCCESS);
      addTechnicalExpectation(assetGroup, detectedEndpoint, null, SUCCESS);
      addTechnicalExpectation(
          assetGroup,
          detectedEndpoint,
          detectedEndpoint.getAgents().getFirst(),
          SUCCESS,
          createCollectorResult(edr, 100.0));
      addTechnicalExpectation(assetGroup, missedEndpoint, null, SUCCESS);
      addTechnicalExpectation(
          assetGroup,
          missedEndpoint,
          missedEndpoint.getAgents().getFirst(),
          SUCCESS,
          createCollectorResult(edr, 0.0));

      // Act
      groupExpectation.setExpectationGroup(true);
      Double atLeastOneAssetScore =
          SecurityPlatformResultUtils.toSecurityPlatformView(groupExpectation, edr.getId())
              .getScore();
      groupExpectation.setExpectationGroup(false);
      Double allAssetsScore =
          SecurityPlatformResultUtils.toSecurityPlatformView(groupExpectation, edr.getId())
              .getScore();

      // Assert
      assertThat(atLeastOneAssetScore).isEqualTo(100.0);
      assertThat(allAssetsScore).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Agents answered only by another platform leave the asset unattributed")
    void given_agentResultsOfAnotherPlatform_should_notAttributeTheAssetExpectation() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform siem = createPlatform("SIEM", "SIEM");
      Endpoint endpoint = createEndpointWithAgents(1);
      TechnicalInjectExpectation assetExpectation =
          addTechnicalExpectation(null, endpoint, null, SUCCESS);
      addTechnicalExpectation(
          null,
          endpoint,
          endpoint.getAgents().getFirst(),
          SUCCESS,
          createCollectorResult(siem, 100.0));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              List.of(assetExpectation), Set.of(edr.getId(), siem.getId()));

      // Assert
      assertThat(resultsByPlatform).containsOnlyKeys(siem.getId());
    }
  }

  @Nested
  @DisplayName("Method: computeResultsBySecurityPlatform")
  class ComputeResultsBySecurityPlatform {

    @Test
    @DisplayName("A platform reporting on every expectation gets the global score")
    void given_onePlatformReportingOnEveryExpectation_should_matchTheGlobalScore() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(DETECTION, SUCCESS, createCollectorResult(edr, 100.0)),
              createExpectation(PREVENTION, SUCCESS, createCollectorResult(edr, 100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(edr.getId()));

      // Assert
      assertThat(resultsByPlatform).containsOnlyKeys(edr.getId());
      assertThat(resultsByPlatform.get(edr.getId()))
          .isEqualTo(
              InjectExpectationResultUtils.getExpectationResultByTypes(
                  expectations, InjectExpectationResultUtils::getScores));
    }

    @Test
    @DisplayName("Several platforms on the same expectations are scored on their own results")
    void given_severalPlatformsWithMixedOutcomes_should_scoreEachPlatformOnItsOwnResults() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform ndr = createPlatform("NDR", "NDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(
                  DETECTION,
                  SUCCESS,
                  createCollectorResult(edr, 100.0),
                  createCollectorResult(ndr, 0.0)),
              createExpectation(PREVENTION, SUCCESS, createCollectorResult(edr, 0.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(edr.getId(), ndr.getId()));

      // Assert
      List<ExpectationResultsByType> edrResults = resultsByPlatform.get(edr.getId());
      assertThat(edrResults).hasSize(2);
      assertThat(resultOfType(edrResults, ExpectationType.DETECTION).getSuccessRate())
          .isEqualTo(1.0);
      assertThat(resultOfType(edrResults, ExpectationType.PREVENTION).getSuccessRate())
          .isEqualTo(0.0);

      List<ExpectationResultsByType> ndrResults = resultsByPlatform.get(ndr.getId());
      assertThat(ndrResults).hasSize(1);
      assertThat(resultOfType(ndrResults, ExpectationType.DETECTION).getSuccessRate())
          .isEqualTo(0.0);
    }

    @Test
    @DisplayName("A platform is not diluted by expectations only other platforms reported on")
    void given_platformsReportingOnDifferentExpectations_should_notDiluteEachOther() {
      // Arrange
      SecurityPlatform workstationEdr = createPlatform("Workstation EDR", "EDR");
      SecurityPlatform serverEdr = createPlatform("Server EDR", "EDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(DETECTION, SUCCESS, createCollectorResult(workstationEdr, 100.0)),
              createExpectation(DETECTION, SUCCESS, createCollectorResult(serverEdr, 100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(workstationEdr.getId(), serverEdr.getId()));

      // Assert
      assertThat(
              resultOfType(resultsByPlatform.get(workstationEdr.getId()), ExpectationType.DETECTION)
                  .getSuccessRate())
          .isEqualTo(1.0);
      assertThat(
              resultOfType(resultsByPlatform.get(serverEdr.getId()), ExpectationType.DETECTION)
                  .getSuccessRate())
          .isEqualTo(1.0);
    }

    @Test
    @DisplayName("A platform only reporting detections is not listed for prevention")
    void given_platformReportingOnlyDetection_should_listNoOtherExpectationType() {
      // Arrange
      SecurityPlatform siem = createPlatform("SIEM", "SIEM");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(DETECTION, SUCCESS, createCollectorResult(siem, 100.0)),
              createExpectation(PREVENTION, SUCCESS));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(siem.getId()));

      // Assert
      assertThat(resultsByPlatform.get(siem.getId()))
          .extracting(ExpectationResultsByType::type)
          .containsExactly(ExpectationType.DETECTION);
    }

    @Test
    @DisplayName("A platform expected to answer but still silent counts as pending, not success")
    void given_pendingPlatformResult_should_countItInThePlatformScore() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(DETECTION, SUCCESS, createCollectorResult(edr, 100.0)),
              createExpectation(DETECTION, PENDING, createCollectorResult(edr, null)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(edr.getId()));

      // Assert
      ExpectationResultsByType detection =
          resultOfType(resultsByPlatform.get(edr.getId()), ExpectationType.DETECTION);
      assertThat(detection.getSuccessRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Only the allowed platforms are attributed, other sources are ignored")
    void given_resultsOutsideTheAllowedPlatforms_should_ignoreThem() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      SecurityPlatform unknownPlatform = createPlatform("Unknown", "EDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(
                  DETECTION,
                  SUCCESS,
                  createCollectorResult(edr, 100.0),
                  createCollectorResult(unknownPlatform, 100.0),
                  createManualResult(100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(edr.getId()));

      // Assert
      assertThat(resultsByPlatform).containsOnlyKeys(edr.getId());
    }

    @Test
    @DisplayName("Expectations without any platform result give nothing to attribute")
    void given_noPlatformResult_should_returnAnEmptyMap() {
      // Arrange
      SecurityPlatform edr = createPlatform("EDR", "EDR");
      BaseInjectExpectation withoutResultsList = createExpectation(PREVENTION, SUCCESS);
      withoutResultsList.setResults(null);
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(DETECTION, SUCCESS, createManualResult(100.0)), withoutResultsList);

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, Set.of(edr.getId()));

      // Assert
      assertThat(resultsByPlatform).isEmpty();
    }

    @Test
    @DisplayName("Platforms are returned in ascending id order")
    void given_severalPlatforms_should_orderThemByPlatformId() {
      // Arrange
      SecurityPlatform first = createPlatform("First", "EDR");
      SecurityPlatform second = createPlatform("Second", "SIEM");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(
                  DETECTION,
                  SUCCESS,
                  createCollectorResult(second, 100.0),
                  createCollectorResult(first, 100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByPlatform =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatform(
              expectations, List.of(second.getId(), first.getId()));

      // Assert
      assertThat(resultsByPlatform.keySet())
          .containsExactlyElementsOf(
              List.of(first.getId(), second.getId()).stream().sorted().toList());
    }

    @Test
    @DisplayName("A vulnerable verdict of one platform of a group wins on an agentless expectation")
    void given_agentlessVulnerabilityWithConflictingGroupResults_should_keepTheVulnerableVerdict() {
      // Arrange
      SecurityPlatform scanner = createPlatform("Scanner", "SIEM");
      SecurityPlatform sameNameScanner = createPlatform("scanner ", "EDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(
                  VULNERABILITY,
                  SUCCESS,
                  createCollectorResult(scanner, 0.0),
                  createCollectorResult(sameNameScanner, 100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByGroup =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatformGroup(
              expectations, Map.of("scanner", Set.of(scanner.getId(), sameNameScanner.getId())));

      // Assert
      assertThat(resultsByGroup).containsOnlyKeys("scanner");
      assertThat(
              resultOfType(resultsByGroup.get("scanner"), ExpectationType.VULNERABILITY)
                  .getSuccessRate())
          .isEqualTo(0.0);
    }

    @Test
    @DisplayName("The best detection verdict of a group wins on an agentless expectation")
    void given_agentlessDetectionWithConflictingGroupResults_should_keepTheBestVerdict() {
      // Arrange
      SecurityPlatform edr = createPlatform("Falcon", "EDR");
      SecurityPlatform xdr = createPlatform("falcon ", "XDR");
      List<BaseInjectExpectation> expectations =
          List.of(
              createExpectation(
                  DETECTION,
                  SUCCESS,
                  createCollectorResult(edr, 0.0),
                  createCollectorResult(xdr, 100.0)));

      // Act
      Map<String, List<ExpectationResultsByType>> resultsByGroup =
          SecurityPlatformResultUtils.computeResultsBySecurityPlatformGroup(
              expectations, Map.of("falcon", Set.of(edr.getId(), xdr.getId())));

      // Assert
      assertThat(
              resultOfType(resultsByGroup.get("falcon"), ExpectationType.DETECTION)
                  .getSuccessRate())
          .isEqualTo(1.0);
    }
  }
}
