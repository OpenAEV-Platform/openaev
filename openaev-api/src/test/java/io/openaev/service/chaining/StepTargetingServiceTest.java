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
import java.util.List;
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
  // hasMapperConditions
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("hasMapperConditions - unsaved step (null id) never hits the condition lookup")
  void givenUnsavedStep_whenCheckingMapperConditions_thenFalseWithoutLookup() {
    assertThat(stepTargetingService.hasMapperConditions(stepWithData("{}"))).isFalse();
    verifyNoInteractions(conditionService);
  }

  @Test
  @DisplayName("hasMapperConditions - MAPPER condition on the step returns true")
  void givenMapperCondition_whenCheckingMapperConditions_thenTrue() {
    Step step = Step.builder().build();
    step.setId("step-1");
    Condition mapperCondition = new Condition();
    mapperCondition.setType(ConditionType.MAPPER);
    when(conditionService.findAllConditionsByStepId("step-1")).thenReturn(List.of(mapperCondition));

    assertThat(stepTargetingService.hasMapperConditions(step)).isTrue();
  }

  @Test
  @DisplayName("hasMapperConditions - only non-MAPPER conditions returns false")
  void givenOnlyDependConditions_whenCheckingMapperConditions_thenFalse() {
    Step step = Step.builder().build();
    step.setId("step-2");
    Condition dependCondition = new Condition();
    dependCondition.setType(ConditionType.DEPEND_ON);
    when(conditionService.findAllConditionsByStepId("step-2")).thenReturn(List.of(dependCondition));

    assertThat(stepTargetingService.hasMapperConditions(step)).isFalse();
  }
}
