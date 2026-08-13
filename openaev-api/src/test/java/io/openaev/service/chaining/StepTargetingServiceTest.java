package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepTargetingService")
class StepTargetingServiceTest {

  @Mock private InjectorContractService injectorContractService;
  @Mock private ConditionService conditionService;

  @InjectMocks private StepTargetingService stepTargetingService;

  // ---------------------------------------------------------------------------
  // Step data fixtures (mirror the serialized inject baked into chaining steps)
  // ---------------------------------------------------------------------------

  /** A payload (technical) step: implant command running on an endpoint. */
  private static final String PAYLOAD_STEP_DATA =
      """
      {"inject_injector_contract":{"injector_contract_id":"contract-payload",
      "injector_contract_payload":{"payload_type":"Command"}}}
      """;

  /** An audience (tabletop) step: email contract, team-based fields only. */
  private static final String EMAIL_STEP_DATA =
      """
      {"inject_injector_contract":{"injector_contract_id":"contract-email",
      "injector_contract_content":
      "{\\"fields\\":[{\\"key\\":\\"teams\\",\\"type\\":\\"team\\"},{\\"key\\":\\"subject\\",\\"type\\":\\"text\\"}]}"},
      "inject_all_teams":false,"inject_teams":[]}
      """;

  /** A technical step whose contract snapshot exposes an asset field. */
  private static final String ASSET_FIELD_STEP_DATA =
      """
      {"inject_injector_contract":{"injector_contract_id":"contract-asset",
      "injector_contract_content":
      "{\\"fields\\":[{\\"key\\":\\"assets\\",\\"type\\":\\"asset\\"}]}"}}
      """;

  /** An external-injector step (nmap-like) driven by the target_selector field key. */
  private static final String TARGET_SELECTOR_STEP_DATA =
      """
      {"inject_injector_contract":{"injector_contract_id":"contract-nmap",
      "injector_contract_content":
      "{\\"fields\\":[{\\"key\\":\\"target_selector\\",\\"type\\":\\"select\\"}]}"}}
      """;

  private Step stepWithData(String data) {
    return Step.builder().data(data).build();
  }

  // ---------------------------------------------------------------------------
  // hasPayload
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("hasPayload - payload contract in step data returns true")
  void givenPayloadContract_whenCheckingHasPayload_thenTrue() {
    assertThat(stepTargetingService.hasPayload(stepWithData(PAYLOAD_STEP_DATA))).isTrue();
  }

  @Test
  @DisplayName("hasPayload - contract without payload returns false")
  void givenContractWithoutPayload_whenCheckingHasPayload_thenFalse() {
    assertThat(stepTargetingService.hasPayload(stepWithData(EMAIL_STEP_DATA))).isFalse();
  }

  @Test
  @DisplayName("hasPayload - step without data returns false")
  void givenStepWithoutData_whenCheckingHasPayload_thenFalse() {
    assertThat(stepTargetingService.hasPayload(stepWithData(null))).isFalse();
  }

  // ---------------------------------------------------------------------------
  // isAssetCentric
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("isAssetCentric - payload step is asset-centric without any contract lookup")
  void givenPayloadStep_whenClassifying_thenAssetCentricWithoutLookup() {
    assertThat(stepTargetingService.isAssetCentric(stepWithData(PAYLOAD_STEP_DATA))).isTrue();
    verifyNoInteractions(injectorContractService);
  }

  @Test
  @DisplayName("isAssetCentric - email contract snapshot (team fields only) is audience-centric")
  void givenEmailSnapshot_whenClassifying_thenAudienceCentric() {
    assertThat(stepTargetingService.isAssetCentric(stepWithData(EMAIL_STEP_DATA))).isFalse();
    verifyNoInteractions(injectorContractService);
  }

  @Test
  @DisplayName("isAssetCentric - contract snapshot with an asset field is asset-centric")
  void givenAssetFieldSnapshot_whenClassifying_thenAssetCentric() {
    assertThat(stepTargetingService.isAssetCentric(stepWithData(ASSET_FIELD_STEP_DATA))).isTrue();
  }

  @Test
  @DisplayName("isAssetCentric - contract snapshot with a target_selector field is asset-centric")
  void givenTargetSelectorSnapshot_whenClassifying_thenAssetCentric() {
    assertThat(stepTargetingService.isAssetCentric(stepWithData(TARGET_SELECTOR_STEP_DATA)))
        .isTrue();
  }

  @Test
  @DisplayName("isAssetCentric - unresolvable contract defaults to asset-centric")
  void givenNoContractInfo_whenClassifying_thenDefaultsToAssetCentric() {
    assertThat(stepTargetingService.isAssetCentric(stepWithData("{}"))).isTrue();
  }

  @Test
  @DisplayName("isAssetCentric - non-primitive contract id falls back to asset-centric, no lookup")
  void givenMalformedContractId_whenClassifying_thenDefaultsToAssetCentricWithoutLookup() {
    String data =
        """
        {"inject_injector_contract":{"injector_contract_id":{"unexpected":"object"}}}
        """;
    assertThat(stepTargetingService.isAssetCentric(stepWithData(data))).isTrue();
    verifyNoInteractions(injectorContractService);
  }

  @Test
  @DisplayName("isAssetCentric - no content snapshot falls back to the live contract")
  void givenNoSnapshot_whenClassifying_thenFallsBackToLiveContract() {
    String data =
        """
        {"inject_injector_contract":{"injector_contract_id":"contract-live"}}
        """;
    InjectorContract liveContract = new InjectorContract();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode content = mapper.createObjectNode();
    content
        .putArray(InjectorContract.CONTRACT_CONTENT_FIELDS)
        .addObject()
        .put(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY, "teams")
        .put(InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE, "team");
    liveContract.setConvertedContent(content);
    when(injectorContractService.injectorContract("contract-live")).thenReturn(liveContract);

    assertThat(stepTargetingService.isAssetCentric(stepWithData(data))).isFalse();
  }

  // ---------------------------------------------------------------------------
  // hasExplicitAudience / hasExplicitTechnicalTargets
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("hasExplicitAudience - all-teams mode counts as explicit audience")
  void givenAllTeams_whenCheckingExplicitAudience_thenTrue() {
    String data = "{\"inject_all_teams\":true}";
    assertThat(stepTargetingService.hasExplicitAudience(stepWithData(data))).isTrue();
  }

  @Test
  @DisplayName("hasExplicitAudience - teams picked in the drawer count as explicit audience")
  void givenDrawerTeams_whenCheckingExplicitAudience_thenTrue() {
    String data = "{\"inject_all_teams\":false,\"inject_teams\":[\"team-1\"]}";
    assertThat(stepTargetingService.hasExplicitAudience(stepWithData(data))).isTrue();
  }

  @Test
  @DisplayName("hasExplicitAudience - empty audience returns false")
  void givenNoAudience_whenCheckingExplicitAudience_thenFalse() {
    assertThat(stepTargetingService.hasExplicitAudience(stepWithData(EMAIL_STEP_DATA))).isFalse();
  }

  @Test
  @DisplayName("hasExplicitTechnicalTargets - assets picked in the drawer count as explicit")
  void givenDrawerAssets_whenCheckingExplicitTechnicalTargets_thenTrue() {
    String data = "{\"inject_assets\":[\"asset-1\"]}";
    assertThat(stepTargetingService.hasExplicitTechnicalTargets(stepWithData(data))).isTrue();
  }

  @Test
  @DisplayName("hasExplicitTechnicalTargets - manual targets in content count as explicit")
  void givenManualTargets_whenCheckingExplicitTechnicalTargets_thenTrue() {
    String data =
        "{\"inject_content\":{\"target_selector\":\"manual\",\"targets\":\"10.0.0.1,10.0.0.2\"}}";
    assertThat(stepTargetingService.hasExplicitTechnicalTargets(stepWithData(data))).isTrue();
  }

  @Test
  @DisplayName("hasExplicitTechnicalTargets - no targets returns false")
  void givenNoTargets_whenCheckingExplicitTechnicalTargets_thenFalse() {
    assertThat(stepTargetingService.hasExplicitTechnicalTargets(stepWithData(PAYLOAD_STEP_DATA)))
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // hasTargetFeedingMapperConditions
  // ---------------------------------------------------------------------------

  private Condition mapperTo(String contentKey) {
    Condition mapperCondition = new Condition();
    mapperCondition.setType(ConditionType.MAPPER);
    mapperCondition.setKey(contentKey);
    return mapperCondition;
  }

  @Test
  @DisplayName("target-feeding mappers - unsaved step (null id) never hits the condition lookup")
  void givenUnsavedStep_whenCheckingTargetFeedingMappers_thenFalseWithoutLookup() {
    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(stepWithData("{}"), true))
        .isFalse();
    verifyNoInteractions(conditionService);
  }

  @Test
  @DisplayName("target-feeding mappers - mapper into manual targets counts for a technical step")
  void givenMapperIntoTargets_whenCheckingTechnicalAxis_thenTrue() {
    Step step = stepWithData(TARGET_SELECTOR_STEP_DATA);
    step.setId("step-1");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-1")))
        .thenReturn(Map.of("step-1", List.of(mapperTo("targets"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, true)).isTrue();
  }

  @Test
  @DisplayName("target-feeding mappers - mapper into a command argument does not count")
  void givenMapperIntoCommandArgument_whenCheckingTechnicalAxis_thenFalse() {
    Step step = stepWithData(PAYLOAD_STEP_DATA);
    step.setId("step-2");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-2")))
        .thenReturn(Map.of("step-2", List.of(mapperTo("target_ip"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, true)).isFalse();
  }

  @Test
  @DisplayName("target-feeding mappers - mapper into recipients counts for an audience step")
  void givenMapperIntoRecipients_whenCheckingAudienceAxis_thenTrue() {
    Step step = stepWithData(EMAIL_STEP_DATA);
    step.setId("step-3");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-3")))
        .thenReturn(Map.of("step-3", List.of(mapperTo("recipients"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, false)).isTrue();
  }

  @Test
  @DisplayName("target-feeding mappers - mapper into the email subject does not count")
  void givenMapperIntoSubject_whenCheckingAudienceAxis_thenFalse() {
    Step step = stepWithData(EMAIL_STEP_DATA);
    step.setId("step-4");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-4")))
        .thenReturn(Map.of("step-4", List.of(mapperTo("subject"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, false)).isFalse();
  }

  @Test
  @DisplayName("target-feeding mappers - mapper into a team-typed contract field counts")
  void givenMapperIntoTeamTypedField_whenCheckingAudienceAxis_thenTrue() {
    // Custom field key resolved as team-typed through the contract snapshot.
    String data =
        """
        {"inject_injector_contract":{"injector_contract_id":"contract-email",
        "injector_contract_content":
        "{\\"fields\\":[{\\"key\\":\\"custom_audience\\",\\"type\\":\\"team\\"}]}"}}
        """;
    Step step = stepWithData(data);
    step.setId("step-5");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-5")))
        .thenReturn(Map.of("step-5", List.of(mapperTo("custom_audience"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, false)).isTrue();
  }

  @Test
  @DisplayName("target-feeding mappers - non-MAPPER conditions never count")
  void givenOnlyDependConditions_whenCheckingTargetFeedingMappers_thenFalse() {
    Step step = stepWithData(EMAIL_STEP_DATA);
    step.setId("step-6");
    Condition dependCondition = new Condition();
    dependCondition.setType(ConditionType.DEPEND_ON);
    dependCondition.setKey("recipients");
    when(conditionService.findAllConditionsByStepIds(Set.of("step-6")))
        .thenReturn(Map.of("step-6", List.of(dependCondition)));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(step, false)).isFalse();
  }

  @Test
  @DisplayName("target-feeding mappers - template-linked mappers are found from the ready step")
  void givenTemplateLinkedMapper_whenCheckingFromReadyStep_thenTrue() {
    Step template = Step.builder().build();
    template.setId("template-1");
    Step readyStep = stepWithData(EMAIL_STEP_DATA);
    readyStep.setId("ready-1");
    readyStep.setStepTemplate(template);
    when(conditionService.findAllConditionsByStepIds(
            new LinkedHashSet<>(List.of("ready-1", "template-1"))))
        .thenReturn(Map.of("template-1", List.of(mapperTo("recipients"))));

    assertThat(stepTargetingService.hasTargetFeedingMapperConditions(readyStep, false)).isTrue();
  }
}
