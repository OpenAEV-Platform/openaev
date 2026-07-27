package io.openaev.service;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.EXPECTATIONS_VULNERABILITY_COLLECTOR_ID;
import static io.openaev.utils.fixtures.InjectExpectationFixture.createVulnerabilityInjectExpectation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.DetectionExpectation;
import io.openaev.expectation.Expectation;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.expectation.ExpectationType;
import io.openaev.expectation.ManualExpectation;
import io.openaev.expectation.PreventionExpectation;
import io.openaev.expectation.VulnerabilityExpectation;
import io.openaev.injectors.common.model.BaseInjectContent;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.fixtures.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest {

  static final Long EXPIRATION_TIME_SIX_HOURS = 21600L;

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private AssetGroupService assetGroupService;
  @Mock private InjectService injectService;
  @Mock private InjectExpectationLockService injectExpectationLockService;

  // Unstubbed: findByExternalReference defaults to Optional.empty(), so vulnerability verdicts
  // keep the legacy Expectations Vulnerability Manager attribution in these tests.
  @Mock private SecurityPlatformRepository securityPlatformRepository;
  @Spy @InjectMocks private InjectExpectationService injectExpectationService;
  @Spy private ObjectMapper mapper = new ObjectMapper();

  private Inject inject;
  private Agent agent;

  @BeforeEach
  void setUp() {
    agent = AgentFixture.createDefaultAgentService();
    inject = InjectFixture.getDefaultInject();
    inject.setExpectations(List.of(createVulnerabilityInjectExpectation(inject, agent)));
    injectExpectationService.mapper = mapper;
  }

  private void mockExpectation(BaseInjectExpectation expectation) {
    doReturn(expectation)
        .when(injectExpectationService)
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  private ExecutionProcessingContext createContext(InjectExecutionInput input) {
    return new ExecutionProcessingContext(inject, agent, input, Map.of());
  }

  private InjectExecutionInput buildDefaultInput(JsonNode structuredOutput) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("message");
    input.setOutputStructured(structuredOutput != null ? String.valueOf(structuredOutput) : null);
    input.setOutputRaw("outputRaw");
    input.setStatus(ExecutionTraceStatus.EXECUTED.toString());
    input.setDuration(10);
    input.setAction(InjectExecutionAction.command_execution);
    return input;
  }

  private void setupVulnerabilityExpectation() {
    BaseInjectExpectation expectation = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(expectation));
    mockExpectation(expectation);
  }

  /** Verifies a single verdict was written and returns its update input. */
  private InjectExpectationUpdateInput captureSingleVerdict() {
    ArgumentCaptor<InjectExpectationUpdateInput> captor =
        ArgumentCaptor.forClass(InjectExpectationUpdateInput.class);
    verify(injectExpectationService, times(1)).updateInjectExpectation(any(), captor.capture());
    return captor.getValue();
  }

  private static io.openaev.model.inject.form.Expectation createFormExpectation(
      BaseInjectExpectation.EXPECTATION_TYPE type) {
    io.openaev.model.inject.form.Expectation expectation =
        ExpectationFixture.createExpectation(type, "test-" + type.name().toLowerCase());
    expectation.setExpectationGroup(false);
    return expectation;
  }

  private void invokeComputeExpectationsForAssetAndAgents(
      List<Expectation> expectations,
      BaseInjectContent content,
      AssetToExecute assetToExecute,
      Inject currentInject,
      String implantType)
      throws Exception {
    Method method =
        InjectExpectationService.class.getDeclaredMethod(
            "computeExpectationsForAssetAndAgents",
            List.class,
            BaseInjectContent.class,
            AssetToExecute.class,
            Inject.class,
            String.class);
    method.setAccessible(true);
    method.invoke(
        injectExpectationService,
        expectations,
        content,
        assetToExecute,
        currentInject,
        implantType);
  }

  private void invokeComputeExpectationsForAssetGroup(
      List<Expectation> expectations, BaseInjectContent content, AssetGroup assetGroup)
      throws Exception {
    Method method =
        InjectExpectationService.class.getDeclaredMethod(
            "computeExpectationsForAssetGroup",
            List.class,
            BaseInjectContent.class,
            AssetGroup.class);
    method.setAccessible(true);
    method.invoke(injectExpectationService, expectations, content, assetGroup);
  }

  @Test
  @DisplayName("Should return early when build input expectations are null or empty")
  void given_nullOrEmptyExpectations_should_notSaveAnyInjectExpectation() {
    // Arrange
    ExecutableInject executableInject = mock(ExecutableInject.class);

    // Act
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, null);
    injectExpectationService.buildAndSaveInjectExpectations(executableInject, List.of());

    // Assert
    verify(injectExpectationRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Should skip expectation building for direct non-atomic non-chaining execution")
  void given_directNonAtomicNonChainingExecution_should_returnBeforeTargetResolution() {
    ExecutableInject executableInject = mock(ExecutableInject.class);
    Injection injection = mock(Injection.class);
    Inject directInject = mock(Inject.class);

    when(executableInject.getInjection()).thenReturn(injection);
    when(injection.getInject()).thenReturn(directInject);
    when(directInject.isAtomicTesting()).thenReturn(false);
    when(executableInject.isDirect()).thenReturn(true);
    when(executableInject.isChainingExecution()).thenReturn(false);

    injectExpectationService.buildAndSaveInjectExpectations(
        executableInject, List.of(mock(Expectation.class)));

    verify(executableInject, never()).getTeams();
    verify(injectExpectationRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Should build expectations for chaining execution even when direct")
  void given_directChainingExecution_should_continueTargetResolution() {
    ExecutableInject executableInject = mock(ExecutableInject.class);
    Injection injection = mock(Injection.class);
    Inject directInject = mock(Inject.class);

    when(executableInject.getInjection()).thenReturn(injection);
    when(injection.getInject()).thenReturn(directInject);
    when(directInject.isAtomicTesting()).thenReturn(false);
    when(executableInject.isDirect()).thenReturn(true);
    when(executableInject.isChainingExecution()).thenReturn(true);
    when(executableInject.getTeams()).thenReturn(List.of());
    when(executableInject.getAssets()).thenReturn(List.of());
    when(executableInject.getAssetGroups()).thenReturn(List.of());

    injectExpectationService.buildAndSaveInjectExpectations(
        executableInject, List.of(mock(Expectation.class)));

    verify(executableInject, atLeastOnce()).getTeams();
  }

  @Test
  @DisplayName(
      "Should skip asset and agent expectation computation when content expectations are empty")
  void given_emptyContentExpectations_should_notComputeAssetAndAgentExpectations()
      throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    List<Expectation> expectations = new ArrayList<>();
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("asset-id");

    // Act
    invokeComputeExpectationsForAssetAndAgents(
        expectations, content, new AssetToExecute(endpoint), inject, "implant");

    // Assert
    assertTrue(expectations.isEmpty());
    verifyNoInteractions(injectService);
  }

  @Test
  @DisplayName("Should ignore unsupported expectation type for asset and agent computation")
  void given_unsupportedExpectationType_should_notCreateAssetAndAgentExpectations()
      throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    content.setExpectations(
        List.of(createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE)));
    List<Expectation> expectations = new ArrayList<>();
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("asset-id");
    inject.setId("inject-id");
    when(injectService.getValueTargetedAssetMap(inject)).thenReturn(Map.of());

    // Act
    invokeComputeExpectationsForAssetAndAgents(
        expectations, content, new AssetToExecute(endpoint), inject, "implant");

    // Assert
    assertTrue(expectations.isEmpty());
    verify(injectService).getValueTargetedAssetMap(inject);
  }

  @Test
  @DisplayName(
      "Should skip asset group expectation computation when content expectations are empty")
  void given_emptyContentExpectations_should_notComputeAssetGroupExpectations() throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    List<Expectation> expectations = new ArrayList<>();
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("ag");
    assetGroup.setId("ag-id");

    // Act
    invokeComputeExpectationsForAssetGroup(expectations, content, assetGroup);

    // Assert
    assertTrue(expectations.isEmpty());
    verifyNoInteractions(assetGroupService);
  }

  @Test
  @DisplayName(
      "Should execute false branches and default branch for asset group expectation matching when no asset matches")
  void
      given_nonMatchingAssetExpectations_should_notCreateAssetGroupExpectationAndCoverFalseBranches()
          throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    content.setExpectations(
        List.of(
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE)));

    Asset matchingAsset = AssetFixture.createDefaultAsset("matching");
    matchingAsset.setId("asset-matching-id");
    Asset nonMatchingAsset = AssetFixture.createDefaultAsset("other");
    nonMatchingAsset.setId("asset-other-id");

    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("ag");
    assetGroup.setId("ag-id");
    when(assetGroupService.assetsFromAssetGroup(assetGroup.getId()))
        .thenReturn(List.of(matchingAsset));

    PreventionExpectation preventionWithNullAsset =
        PreventionExpectation.preventionExpectationForAsset(
            100.0, "p-null", "desc", matchingAsset, assetGroup, 60L);
    preventionWithNullAsset.setAsset(null);
    PreventionExpectation preventionWithNonMatchingAsset =
        PreventionExpectation.preventionExpectationForAsset(
            100.0, "p-other", "desc", nonMatchingAsset, assetGroup, 60L);

    DetectionExpectation detectionWithNullAsset =
        DetectionExpectation.detectionExpectationForAsset(
            100.0, "d-null", "desc", matchingAsset, assetGroup, 60L);
    detectionWithNullAsset.setAsset(null);
    DetectionExpectation detectionWithNonMatchingAsset =
        DetectionExpectation.detectionExpectationForAsset(
            100.0, "d-other", "desc", nonMatchingAsset, assetGroup, 60L);

    VulnerabilityExpectation vulnerabilityWithNullAsset = new VulnerabilityExpectation();
    vulnerabilityWithNullAsset.setName("v-null");
    vulnerabilityWithNullAsset.setScore(100.0);
    vulnerabilityWithNullAsset.setAsset(null);
    VulnerabilityExpectation vulnerabilityWithNonMatchingAsset = new VulnerabilityExpectation();
    vulnerabilityWithNonMatchingAsset.setName("v-other");
    vulnerabilityWithNonMatchingAsset.setScore(100.0);
    vulnerabilityWithNonMatchingAsset.setAsset(nonMatchingAsset);

    ManualExpectation manualWithNullAsset =
        ManualExpectation.manualExpectationForAsset(
            100.0, "m-null", "desc", matchingAsset, assetGroup, 60L);
    manualWithNullAsset.setAsset(null);
    ManualExpectation manualWithNonMatchingAsset =
        ManualExpectation.manualExpectationForAsset(
            100.0, "m-other", "desc", nonMatchingAsset, assetGroup, 60L);

    List<Expectation> expectations =
        new ArrayList<>(
            List.of(
                preventionWithNullAsset,
                preventionWithNonMatchingAsset,
                detectionWithNullAsset,
                detectionWithNonMatchingAsset,
                vulnerabilityWithNullAsset,
                vulnerabilityWithNonMatchingAsset,
                manualWithNullAsset,
                manualWithNonMatchingAsset));
    int initialSize = expectations.size();

    // Act
    invokeComputeExpectationsForAssetGroup(expectations, content, assetGroup);

    // Assert
    assertEquals(initialSize, expectations.size());
    verify(assetGroupService).assetsFromAssetGroup(assetGroup.getId());
  }

  @Test
  @DisplayName(
      "Should execute all supported switch branches for asset and agent expectation computation")
  void given_supportedExpectationTypes_should_computeAssetAndAgentExpectationsAcrossAllCases()
      throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    content.setExpectations(
        List.of(
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL)));

    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("asset-id");
    endpoint.setAgents(List.of());
    inject.setId("inject-id");

    when(injectService.getValueTargetedAssetMap(inject)).thenReturn(Map.of());
    List<Expectation> expectations = new ArrayList<>();

    // Act
    invokeComputeExpectationsForAssetAndAgents(
        expectations, content, new AssetToExecute(endpoint), inject, "implant");

    // Assert
    assertNotNull(expectations);
    verify(injectService).getValueTargetedAssetMap(inject);
  }

  @Test
  @DisplayName(
      "Should create one asset group expectation per supported type when at least one asset matches")
  void given_matchingAssetExpectations_should_createAssetGroupExpectationsForAllSupportedTypes()
      throws Exception {
    // Arrange
    BaseInjectContent content = new BaseInjectContent();
    content.setExpectations(
        List.of(
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY),
            createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL)));

    Asset matchingAsset = AssetFixture.createDefaultAsset("matching");
    matchingAsset.setId("asset-matching-id");
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("ag-match");
    assetGroup.setId("ag-match-id");
    when(assetGroupService.assetsFromAssetGroup(assetGroup.getId()))
        .thenReturn(List.of(matchingAsset));

    PreventionExpectation preventionMatching =
        PreventionExpectation.preventionExpectationForAsset(
            100.0, "p", "desc", matchingAsset, assetGroup, 60L);
    DetectionExpectation detectionMatching =
        DetectionExpectation.detectionExpectationForAsset(
            100.0, "d", "desc", matchingAsset, assetGroup, 60L);
    VulnerabilityExpectation vulnerabilityMatching = new VulnerabilityExpectation();
    vulnerabilityMatching.setName("v");
    vulnerabilityMatching.setDescription("desc");
    vulnerabilityMatching.setScore(100.0);
    vulnerabilityMatching.setAsset(matchingAsset);
    vulnerabilityMatching.setAssetGroup(assetGroup);
    vulnerabilityMatching.setExpirationTime(60L);
    ManualExpectation manualMatching =
        ManualExpectation.manualExpectationForAsset(
            100.0, "m", "desc", matchingAsset, assetGroup, 60L);

    List<Expectation> expectations =
        new ArrayList<>(
            List.of(preventionMatching, detectionMatching, vulnerabilityMatching, manualMatching));
    int initialSize = expectations.size();

    // Act
    invokeComputeExpectationsForAssetGroup(expectations, content, assetGroup);

    // Assert
    assertEquals(initialSize + 4, expectations.size());
    verify(assetGroupService).assetsFromAssetGroup(assetGroup.getId());
  }

  @Test
  @DisplayName("Should return all prevention expectations when none expired")
  void shouldReturnAllPreventionExpectationsWhenNoneExpired() {
    BaseInjectExpectation expectation1 =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    BaseInjectExpectation expectation2 =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<BaseInjectExpectation> result =
        injectExpectationService.preventionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all detection expectations when none expired")
  void shouldReturnAllDetectionExpectationsWhenNoneExpired() {
    BaseInjectExpectation expectation1 =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    BaseInjectExpectation expectation2 =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<BaseInjectExpectation> result =
        injectExpectationService.detectionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all manual expectations when none expired")
  void shouldReturnAllManualExpectationsWhenNoneExpired() {
    BaseInjectExpectation expectation1 =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);
    BaseInjectExpectation expectation2 =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);
    when(injectExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<BaseInjectExpectation> result =
        injectExpectationService.manualExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Agent path: empty structured output concludes not vulnerable")
  void shouldSetNotVulnerableWhenEmptyStructuredOutput() {
    setupVulnerabilityExpectation();

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    InjectExpectationUpdateInput input = captureSingleVerdict();
    assertEquals(Boolean.TRUE, input.getIsSuccess());
    assertEquals(ExpectationType.VULNERABILITY.successLabel, input.getResult());
  }

  @Test
  @DisplayName("Agent path: empty CVE array concludes not vulnerable")
  void shouldSetNotVulnerableWhenStructuredOutputIsEmptyArray() {
    // isArray()=true but size()=0 -> not vulnerable
    setupVulnerabilityExpectation();

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createArrayNode());

    assertEquals(Boolean.TRUE, captureSingleVerdict().getIsSuccess());
  }

  @Test
  @DisplayName("Agent path: non-empty CVE array concludes vulnerable for the agent's asset")
  void shouldSetVulnerableWhenStructuredOutputIsNonEmptyArray() {
    // isArray()=true and size()>0 -> vulnerable
    ArrayNode structuredOutput = mapper.createArrayNode();
    structuredOutput.addObject().put("id", "CVE-2025-9999");
    setupVulnerabilityExpectation();

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), structuredOutput);

    InjectExpectationUpdateInput input = captureSingleVerdict();
    assertEquals(Boolean.FALSE, input.getIsSuccess());
    assertEquals(ExpectationType.VULNERABILITY.failureLabel, input.getResult());
    assertEquals(EXPECTATIONS_VULNERABILITY_COLLECTOR_ID, input.getCollectorId());
  }

  @Test
  @DisplayName("Should do nothing when no vulnerability expectations match the agent")
  void shouldDoNothingWhenNoVulnerabilityExpectationsForAgent() {
    // Expectation belongs to a different agent -> filtered out -> early return
    Agent otherAgent = AgentFixture.createDefaultAgentService();
    BaseInjectExpectation expectationForOtherAgent =
        createVulnerabilityInjectExpectation(inject, otherAgent);
    inject.setExpectations(List.of(expectationForOtherAgent));

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    verify(injectExpectationService, never())
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  @Test
  @DisplayName("Should do nothing when expectations are not of vulnerability type")
  void shouldDoNothingWhenExpectationsAreNotVulnerabilityType() {
    // Only non-VULNERABILITY expectations -> filtered out -> early return
    BaseInjectExpectation prevention =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    BaseInjectExpectation detection =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    inject.setExpectations(List.of(prevention, detection));

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    verify(injectExpectationService, never())
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  @Test
  @DisplayName(
      "Should do nothing for an agent execution when the expectation is not bound to an agent")
  void shouldDoNothingWhenExpectationHasNullAgent() {
    // exp.getAgent() == null while ctx.agent() != null -> filtered out -> early return
    BaseInjectExpectation expectationWithNullAgent =
        createVulnerabilityInjectExpectation(inject, null);
    inject.setExpectations(List.of(expectationWithNullAgent));

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    verify(injectExpectationService, never())
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  @Test
  @DisplayName("Should do nothing when inject has no expectations")
  void shouldDoNothingWhenInjectHasNoExpectations() {
    inject.setExpectations(List.of());

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    verify(injectExpectationService, never())
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  @Test
  @DisplayName("Should call update for each vulnerability expectation")
  void shouldCallUpdateForEachVulnerabilityExpectation() {
    // Two vulnerability expectations for the same agent
    BaseInjectExpectation exp1 = createVulnerabilityInjectExpectation(inject, agent);
    BaseInjectExpectation exp2 = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(exp1, exp2));
    doReturn(exp1)
        .when(injectExpectationService)
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));

    injectExpectationService.matchesVulnerabilityExpectations(
        createContext(buildDefaultInput(null)), mapper.createObjectNode());

    // updateInjectExpectation called once per expectation
    verify(injectExpectationService, times(2))
        .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
  }

  @Nested
  @DisplayName("matchesVulnerabilityExpectations - injector path (agent == null)")
  class MatchesVulnerabilityExpectationsInjectorPath {

    private VulnerabilityInjectExpectation expectationAssetOne;
    private VulnerabilityInjectExpectation expectationAssetTwo;
    private VulnerabilityInjectExpectation expectationGroup;

    @BeforeEach
    void setUpInjectorPath() {
      Asset assetOne = AssetFixture.createDefaultAsset("vulnerable-asset");
      assetOne.setId("asset-1");
      Asset assetTwo = AssetFixture.createDefaultAsset("clean-asset");
      assetTwo.setId("asset-2");
      AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("group");
      assetGroup.setId("group-1");

      expectationAssetOne = createVulnerabilityInjectExpectation(inject, null);
      expectationAssetOne.setId("exp-asset-1");
      expectationAssetOne.setAsset(assetOne);
      expectationAssetOne.setAssetGroup(assetGroup);

      expectationAssetTwo = createVulnerabilityInjectExpectation(inject, null);
      expectationAssetTwo.setId("exp-asset-2");
      expectationAssetTwo.setAsset(assetTwo);
      expectationAssetTwo.setAssetGroup(assetGroup);

      expectationGroup = createVulnerabilityInjectExpectation(inject, null);
      expectationGroup.setId("exp-group");
      expectationGroup.setAssetGroup(assetGroup);

      inject.setExpectations(List.of(expectationAssetOne, expectationAssetTwo, expectationGroup));
      doReturn(expectationAssetOne)
          .when(injectExpectationService)
          .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
    }

    private ExecutionProcessingContext injectorContext(ArrayNode structuredOutput) {
      return new ExecutionProcessingContext(
          inject, null, buildDefaultInput(structuredOutput), Map.of());
    }

    private InjectExpectationUpdateInput capturedVerdict(String expectationId) {
      ArgumentCaptor<InjectExpectationUpdateInput> captor =
          ArgumentCaptor.forClass(InjectExpectationUpdateInput.class);
      verify(injectExpectationService).updateInjectExpectation(eq(expectationId), captor.capture());
      return captor.getValue();
    }

    @Test
    @DisplayName("Only the asset carrying the CVE is marked vulnerable, siblings stay clean")
    void shouldMarkOnlyAttributedAssetVulnerable() {
      ArrayNode structuredOutput = mapper.createArrayNode();
      ObjectNode cve = structuredOutput.addObject();
      cve.put("id", "CVE-2025-0001").put("host", "https://vulnerable-host").put("severity", "7.5");
      cve.putArray("asset_id").add("asset-1");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      assertEquals(Boolean.FALSE, capturedVerdict("exp-asset-1").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-asset-2").getIsSuccess());
      // Default group semantics: one vulnerable asset makes the group vulnerable, and the group
      // verdict is written with its own attributed source result.
      assertEquals(Boolean.FALSE, capturedVerdict("exp-group").getIsSuccess());
    }

    @Test
    @DisplayName("All assets stay clean when the CVE is attributed to an untargeted asset")
    void shouldKeepAllAssetsCleanWhenCveAttributedElsewhere() {
      ArrayNode structuredOutput = mapper.createArrayNode();
      ObjectNode cve = structuredOutput.addObject();
      cve.put("id", "CVE-2025-0002").put("host", "https://other-host").put("severity", "5.0");
      cve.putArray("asset_id").add("asset-unrelated");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      assertEquals(Boolean.TRUE, capturedVerdict("exp-asset-1").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-asset-2").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-group").getIsSuccess());
    }

    @Test
    @DisplayName("Host matching attributes the CVE when asset_id is missing")
    void shouldAttributeCveThroughHostFallback() {
      Endpoint endpointOne = EndpointFixture.createEndpoint();
      endpointOne.setId("asset-1");
      when(injectService.getValueTargetedAssetMap(inject))
          .thenReturn(Map.of("vulnerable-host", endpointOne));

      ArrayNode structuredOutput = mapper.createArrayNode();
      structuredOutput
          .addObject()
          .put("id", "CVE-2025-0003")
          .put("host", "https://vulnerable-host:8443/path")
          .put("severity", "9.8");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      assertEquals(Boolean.FALSE, capturedVerdict("exp-asset-1").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-asset-2").getIsSuccess());
      assertEquals(Boolean.FALSE, capturedVerdict("exp-group").getIsSuccess());
    }

    @Test
    @DisplayName("Findings without any attribution fall back to the legacy blanket verdict")
    void shouldFallBackToBlanketVerdictWithoutAttribution() {
      when(injectService.getValueTargetedAssetMap(inject)).thenReturn(Map.of());

      ArrayNode structuredOutput = mapper.createArrayNode();
      structuredOutput
          .addObject()
          .put("id", "CVE-2025-0004")
          .put("host", "https://unknown-host")
          .put("severity", "6.1");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      assertEquals(Boolean.FALSE, capturedVerdict("exp-asset-1").getIsSuccess());
      assertEquals(Boolean.FALSE, capturedVerdict("exp-asset-2").getIsSuccess());
      assertEquals(Boolean.FALSE, capturedVerdict("exp-group").getIsSuccess());
    }

    @Test
    @DisplayName(
        "Expectation-group semantics keep the group clean while at least one asset is clean")
    void shouldKeepExpectationGroupCleanWhenOneAssetIsClean() {
      expectationGroup.setExpectationGroup(true);

      ArrayNode structuredOutput = mapper.createArrayNode();
      ObjectNode cve = structuredOutput.addObject();
      cve.put("id", "CVE-2025-0005").put("host", "https://vulnerable-host").put("severity", "7.5");
      cve.putArray("asset_id").add("asset-1");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      assertEquals(Boolean.FALSE, capturedVerdict("exp-asset-1").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-asset-2").getIsSuccess());
      assertEquals(Boolean.TRUE, capturedVerdict("exp-group").getIsSuccess());
    }
  }

  // ========================================================================
  // findDistinctInjectIdsByInjectExpectationIds Tests
  // ========================================================================
  @Nested
  @DisplayName("findDistinctInjectIdsByInjectExpectationIds")
  class FindDistinctInjectIdsByInjectExpectationIdsTests {

    @Captor private ArgumentCaptor<Set<String>> expectationIdsCaptor;

    private static Stream<Arguments> testCases() {
      String expectationId1 = UUID.randomUUID().toString();
      String expectationId2 = UUID.randomUUID().toString();
      String expectationId3 = UUID.randomUUID().toString();

      String injectId1 = UUID.randomUUID().toString();
      String injectId2 = UUID.randomUUID().toString();

      return Stream.of(
          Arguments.of(
              "multiple expectation IDs returning multiple inject IDs",
              Set.of(expectationId1, expectationId2, expectationId3),
              Set.of(injectId1, injectId2)),
          Arguments.of(
              "multiple expectation IDs returning single inject ID",
              Set.of(expectationId1, expectationId2),
              Set.of(injectId1)),
          Arguments.of("single expectation ID", Set.of(expectationId1), Set.of(injectId1)),
          Arguments.of("empty expectation IDs", Collections.emptySet(), Collections.emptySet()),
          Arguments.of(
              "expectation IDs with no matching injects",
              Set.of(expectationId1, expectationId2),
              Collections.emptySet()));
    }

    @ParameterizedTest(name = "should handle {0}")
    @MethodSource("testCases")
    void shouldReturnDistinctInjectIds(
        String name, Set<String> expectationIds, Set<String> expectedInjectIds) {
      // Prepare
      when(injectExpectationRepository.findDistinctInjectIdsByInjectExpectationIds(expectationIds))
          .thenReturn(expectedInjectIds);

      // Act
      Set<String> result =
          injectExpectationService.findDistinctInjectIdsByInjectExpectationIds(expectationIds);

      // Assert
      verify(injectExpectationRepository)
          .findDistinctInjectIdsByInjectExpectationIds(expectationIdsCaptor.capture());
      assertEquals(expectationIds, expectationIdsCaptor.getValue());
      assertNotNull(result);
      assertEquals(expectedInjectIds.size(), result.size());
      assertEquals(expectedInjectIds, result);
      verifyNoMoreInteractions(injectExpectationRepository);
    }
  }

  @Nested
  @DisplayName("appendExpectationSignatures")
  class AppendExpectationSignaturesTests {

    @Test
    @DisplayName("Returns immediately when signatures are empty")
    void givenEmptySignaturesShouldReturnWithoutSideEffects() {
      assertDoesNotThrow(
          () ->
              injectExpectationService.appendExpectationSignatures(
                  "inject-id",
                  "agent-id",
                  null,
                  null,
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  List.of()));

      verifyNoInteractions(injectExpectationRepository);
    }

    @Test
    @DisplayName("Returns without side effects for a non-technical expectation type")
    void givenNonTechnicalExpectationTypeShouldReturnWithoutSideEffects() {
      List<ExpectationSignature> signatures =
          List.of(new ExpectationSignature("signature-type", "signature-value"));

      assertDoesNotThrow(
          () ->
              injectExpectationService.appendExpectationSignatures(
                  "inject-id",
                  "agent-id",
                  null,
                  null,
                  BaseInjectExpectation.EXPECTATION_TYPE.MANUAL,
                  signatures));

      verifyNoInteractions(injectExpectationRepository);
      verifyNoInteractions(injectExpectationLockService);
    }

    @Test
    @DisplayName("Delegates to lock service for each matching expectation")
    void givenMatchingExpectationsShouldDelegateToLockService() {
      DetectionInjectExpectation first = new DetectionInjectExpectation();
      first.setId("exp-1");
      DetectionInjectExpectation second = new DetectionInjectExpectation();
      second.setId("exp-2");
      when(injectExpectationRepository.findAllByInjectAndAgent("inject-id", "agent-id"))
          .thenReturn(List.of(first, second));

      injectExpectationService.appendExpectationSignatures(
          "inject-id",
          "agent-id",
          null,
          null,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          List.of(new ExpectationSignature("signature-type", "signature-value")));

      verify(injectExpectationLockService, times(2))
          .applySignaturesForExpectationWithLock(anyString(), any());
    }
  }

  @Nested
  @DisplayName("findMergedExpectationsByInjectAndTargetAndTargetType for assets")
  class AssetSecurityPlatformEnrichmentTests {

    @Test
    @DisplayName("Asset expectations mirror their agents' security-platform results")
    void assetExpectationsAreEnrichedWithAgentSecurityPlatformResults() {
      DetectionInjectExpectation assetExpectation = new DetectionInjectExpectation();
      assetExpectation.setId("asset-expectation");
      DetectionInjectExpectation agentExpectation = new DetectionInjectExpectation();
      agentExpectation.setId("agent-expectation");
      InjectExpectationResult collectorResult =
          InjectExpectationResult.builder()
              .sourceId("collector-1")
              .sourceType("collector")
              .sourceName("EDR Collector")
              .result("Success")
              .build();
      agentExpectation.setResults(new ArrayList<>(List.of(collectorResult)));
      when(injectExpectationRepository.findAllByInjectAndAsset("inject-id", "asset-id"))
          .thenReturn(List.of(assetExpectation));
      when(injectExpectationRepository.findAllAgentExpectationsByInjectAndAsset(
              "inject-id", "asset-id"))
          .thenReturn(List.of(agentExpectation));

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "asset-id", "parent-id", "ASSETS");

      assertEquals(1, merged.size());
      assertEquals(List.of(collectorResult), merged.get(0).getResults());
      // The enrichment must stay display-only: the persistent entity is untouched.
      assertTrue(assetExpectation.getResults().isEmpty());
    }

    @Test
    @DisplayName("Agentless assets keep their own expectation results unchanged")
    void agentlessAssetExpectationsAreReturnedUnchanged() {
      DetectionInjectExpectation assetExpectation = new DetectionInjectExpectation();
      assetExpectation.setId("asset-expectation");
      when(injectExpectationRepository.findAllByInjectAndAsset("inject-id", "asset-id"))
          .thenReturn(List.of(assetExpectation));
      when(injectExpectationRepository.findAllAgentExpectationsByInjectAndAsset(
              "inject-id", "asset-id"))
          .thenReturn(List.of());

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "asset-id", "parent-id", "ASSETS");

      assertEquals(List.of(assetExpectation), merged);
    }
  }
}
