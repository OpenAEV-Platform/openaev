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
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.config.DefaultOpenAEVPrincipal;
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
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest {

  static final Long EXPIRATION_TIME_SIX_HOURS = 21600L;

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private AssetGroupService assetGroupService;
  @Mock private InjectService injectService;
  @Mock private InjectExpectationLockService injectExpectationLockService;
  @Mock private CollectorService collectorService;
  @Mock private InjectorContractContentUtils injectorContractContentUtils;
  @Mock private AuditLogger auditLogger;

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
    ReflectionTestUtils.setField(injectExpectationService, "auditLogger", Optional.of(auditLogger));
  }

  @org.junit.jupiter.api.AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
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

  private AuditEvent invokeExpectationResultAudit(
      BaseInjectExpectation expectation, InjectExpectationResult sourceResult) throws Exception {
    Method method =
        InjectExpectationService.class.getDeclaredMethod(
            "logExpectationResultEvent",
            BaseInjectExpectation.class,
            InjectExpectationResult.class);
    method.setAccessible(true);
    method.invoke(injectExpectationService, expectation, sourceResult);

    ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditLogger).logEvent(auditEventCaptor.capture());
    return auditEventCaptor.getValue();
  }

  @Nested
  @DisplayName("Expectation result audit logging")
  class ExpectationResultAuditLogging {

    @Test
    @DisplayName("Automatic expectation result emits EXPECTATION_RESULT with SYSTEM origin")
    void
        given_automaticExpectationResult_should_emitExpectationResultAuditEvent_withAutomaticSourceAndSystemOrigin()
            throws Exception {
      // Arrange
      BaseInjectExpectation expectation = createVulnerabilityInjectExpectation(inject, agent);
      expectation.setId("expectation-automatic");
      expectation.setExpectedScore(50.0);
      expectation.setScore(75.0);
      InjectExpectationResult sourceResult =
          InjectExpectationResult.builder()
              .sourceId("collector-1")
              .sourceName("EDR Collector")
              .sourceType("automatic")
              .date("2026-08-05T10:15:30Z")
              .build();

      // Act
      AuditEvent event = invokeExpectationResultAudit(expectation, sourceResult);

      // Assert
      assertEquals(AuditEventScope.EXPECTATION_RESULT, event.getEventScope());
      assertEquals(AuditEventOrigin.SYSTEM, event.getOrigin());
      assertEquals("automatic", event.getContextData().get("source_type"));
    }

    @Test
    @DisplayName("Manual expectation result emits EXPECTATION_RESULT with REQUEST origin and user")
    void
        given_manualExpectationResult_should_emitExpectationResultAuditEvent_withManualSourceUserAttributionAndRequestOrigin()
            throws Exception {
      // Arrange
      String userId = "user-42";
      DefaultOpenAEVPrincipal principal =
          new DefaultOpenAEVPrincipal(userId, List.of(), false, "en");
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(principal, null));

      BaseInjectExpectation expectation = createVulnerabilityInjectExpectation(inject, agent);
      expectation.setId("expectation-manual");
      expectation.setExpectedScore(50.0);
      expectation.setScore(null);
      InjectExpectationResult sourceResult =
          InjectExpectationResult.builder()
              .sourceId("manual-source")
              .sourceName(userId)
              .sourceType("manual")
              .build();

      // Act
      AuditEvent event = invokeExpectationResultAudit(expectation, sourceResult);

      // Assert
      assertEquals(AuditEventScope.EXPECTATION_RESULT, event.getEventScope());
      assertEquals(AuditEventOrigin.SYSTEM, event.getOrigin());
      assertEquals("manual", event.getContextData().get("source_type"));
      assertEquals(userId, event.getContextData().get("source"));
    }
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
      "Reset/relaunch fallback: contract expectations are used when the inject content has none")
  void given_contentWithoutExpectations_should_fallBackToContractExpectations() throws Exception {
    // Inject created before its contract declared predefined expectations: stored content has no
    // "expectations" field, so resetting + relaunching the simulation used to create none.
    ObjectNode storedContent = mapper.createObjectNode();
    inject.setContent(storedContent);
    InjectorContract contract = mock(InjectorContract.class);
    // Agentless injector (Nuclei-like): asset-level expectations are created without agents.
    when(contract.getNeedsExecutorEffective()).thenReturn(false);
    inject.setInjectorContract(contract);
    ReflectionTestUtils.setField(inject, "tenant", TenantFixture.getTenant());
    // @Resource field, not constructor-injected: provide the default expiration configuration.
    ReflectionTestUtils.setField(
        injectExpectationService,
        "expectationPropertiesConfig",
        new io.openaev.expectation.ExpectationPropertiesConfig());

    BaseInjectContent contractContent = new BaseInjectContent();
    contractContent.setExpectations(
        List.of(createFormExpectation(BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY)));
    ObjectNode enrichedContent = mapper.valueToTree(contractContent);
    when(injectorContractContentUtils.setExpectations(eq(contract), any(ObjectNode.class)))
        .thenReturn(enrichedContent);

    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("asset-id");
    endpoint.setAgents(List.of());
    when(injectService.getValueTargetedAssetMap(inject)).thenReturn(Map.of());
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());

    ExecutableInject executableInject = mock(ExecutableInject.class);
    Injection injection = mock(Injection.class);
    when(executableInject.getInjection()).thenReturn(injection);
    when(injection.getInject()).thenReturn(inject);
    when(executableInject.isDirect()).thenReturn(false);
    when(executableInject.getTeams()).thenReturn(List.of());
    when(executableInject.getAssets()).thenReturn(List.of(endpoint));
    when(executableInject.getAssetGroups()).thenReturn(List.of());

    injectExpectationService.computeAndSaveExpectations(
        executableInject, inject, "implant", List.of(new AssetToExecute(endpoint)));

    // The contract's predefined expectations were resolved and materialized as a persisted
    // asset-level vulnerability expectation.
    verify(injectorContractContentUtils).setExpectations(eq(contract), any(ObjectNode.class));
    ArgumentCaptor<List<BaseInjectExpectation>> savedCaptor = ArgumentCaptor.captor();
    verify(injectExpectationRepository).saveAll(savedCaptor.capture());
    List<BaseInjectExpectation> saved = savedCaptor.getValue();
    assertEquals(1, saved.size());
    VulnerabilityInjectExpectation savedExpectation =
        assertInstanceOf(VulnerabilityInjectExpectation.class, saved.get(0));
    assertEquals("asset-id", savedExpectation.getAsset().getId());
    assertNull(savedExpectation.getAgent());
  }

  @Test
  @DisplayName(
      "Reset/relaunch fallback: an explicit empty expectations list is respected, never overridden")
  void given_contentWithExplicitlyEmptyExpectations_should_notFallBackToContractExpectations()
      throws Exception {
    // The user deliberately removed every expectation from the inject: the stored content carries
    // an explicit empty "expectations" array (that is what the inject form persists on removal).
    // Execution must respect that customization instead of forcing the contract's predefined
    // expectations back on every launch - drift realignment is the opt-in way to restore them.
    ObjectNode storedContent = mapper.createObjectNode();
    storedContent.putArray("expectations");
    inject.setContent(storedContent);
    inject.setInjectorContract(mock(InjectorContract.class));

    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("asset-id");
    endpoint.setAgents(List.of());

    ExecutableInject executableInject = mock(ExecutableInject.class);
    Injection injection = mock(Injection.class);
    when(executableInject.getInjection()).thenReturn(injection);
    when(injection.getInject()).thenReturn(inject);
    when(executableInject.getAssetGroups()).thenReturn(List.of());

    injectExpectationService.computeAndSaveExpectations(
        executableInject, inject, "implant", List.of(new AssetToExecute(endpoint)));

    // No contract fallback and no expectation persisted: the empty list is the user's choice.
    verify(injectorContractContentUtils, never()).setExpectations(any(), any());
    verify(injectExpectationRepository, never()).saveAll(any());
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
      // Lenient: the security-platform attribution test routes the verdicts through
      // updateInjectExpectationFromSecurityPlatform instead and never hits this stub.
      lenient()
          .doReturn(expectationAssetOne)
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
        "Verdicts are attributed to the injector's security platform for assets AND their group")
    void shouldAttributeVerdictsToInjectorSecurityPlatform() {
      Injector injector = new Injector();
      injector.setType("openaev_nuclei");
      inject.setInjector(injector);
      SecurityPlatform securityPlatform =
          SecurityPlatformFixture.createDefault("Nuclei", "VULNERABILITY_SCANNER");
      securityPlatform.setId("nuclei-platform");
      when(securityPlatformRepository.findByExternalReference("openaev_nuclei"))
          .thenReturn(Optional.of(securityPlatform));
      doReturn(expectationAssetOne)
          .when(injectExpectationService)
          .updateInjectExpectationFromSecurityPlatform(
              any(), any(InjectExpectationUpdateInput.class), any(SecurityPlatform.class));

      ArrayNode structuredOutput = mapper.createArrayNode();
      ObjectNode cve = structuredOutput.addObject();
      cve.put("id", "CVE-2025-0008").put("host", "https://vulnerable-host").put("severity", "7.5");
      cve.putArray("asset_id").add("asset-1");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      // The vulnerable asset AND its group are both concluded through the security platform
      // path, so the group row is immediately attributed to the Nuclei platform.
      ArgumentCaptor<InjectExpectationUpdateInput> assetInput =
          ArgumentCaptor.forClass(InjectExpectationUpdateInput.class);
      verify(injectExpectationService)
          .updateInjectExpectationFromSecurityPlatform(
              eq("exp-asset-1"), assetInput.capture(), eq(securityPlatform));
      assertEquals(Boolean.FALSE, assetInput.getValue().getIsSuccess());
      assertNull(assetInput.getValue().getCollectorId());

      ArgumentCaptor<InjectExpectationUpdateInput> groupInput =
          ArgumentCaptor.forClass(InjectExpectationUpdateInput.class);
      verify(injectExpectationService)
          .updateInjectExpectationFromSecurityPlatform(
              eq("exp-group"), groupInput.capture(), eq(securityPlatform));
      assertEquals(Boolean.FALSE, groupInput.getValue().getIsSuccess());
      assertNull(groupInput.getValue().getCollectorId());

      verify(injectExpectationService)
          .updateInjectExpectationFromSecurityPlatform(
              eq("exp-asset-2"), any(InjectExpectationUpdateInput.class), eq(securityPlatform));
      // No verdict falls back to the generic Expectations Vulnerability Manager collector.
      verify(injectExpectationService, never())
          .updateInjectExpectation(any(), any(InjectExpectationUpdateInput.class));
    }

    @Test
    @DisplayName(
        "Mixed attribution: an unattributable finding triggers the blanket verdict for all assets")
    void shouldFallBackToBlanketVerdictOnMixedAttribution() {
      when(injectService.getValueTargetedAssetMap(inject)).thenReturn(Map.of());

      ArrayNode structuredOutput = mapper.createArrayNode();
      ObjectNode attributedCve = structuredOutput.addObject();
      attributedCve
          .put("id", "CVE-2025-0006")
          .put("host", "https://vulnerable-host")
          .put("severity", "7.5");
      attributedCve.putArray("asset_id").add("asset-1");
      structuredOutput
          .addObject()
          .put("id", "CVE-2025-0007")
          .put("host", "https://unknown-host")
          .put("severity", "6.1");

      injectExpectationService.matchesVulnerabilityExpectations(
          injectorContext(structuredOutput), structuredOutput);

      // The second finding cannot be attributed to any targeted asset: rather than silently
      // dropping it, every asset falls back to the legacy blanket verdict.
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
    @DisplayName(
        "Vulnerability display merge drops the expiration manager row when a platform answered")
    void vulnerabilityDisplayMergeDropsExpirationManagerRowWhenPlatformAnswered() {
      // Regression: the asset target-results view unions the agent children's collector results
      // onto the asset expectation. The agents legitimately expire to the vulnerability default
      // "Not vulnerable" (an agentless scanner never fills them), so the union displayed a
      // redundant - or contradictory - "Expectations Expiration Manager" row next to the genuine
      // scan verdict persisted on the asset row.
      VulnerabilityInjectExpectation assetExpectation = new VulnerabilityInjectExpectation();
      assetExpectation.setId("asset-expectation");
      InjectExpectationResult nucleiResult =
          InjectExpectationResult.builder()
              .sourceId("nuclei-security-platform")
              .sourceType("security-platform")
              .sourceName("Nuclei")
              .result("Not vulnerable")
              .score(100.0)
              .build();
      assetExpectation.setResults(new ArrayList<>(List.of(nucleiResult)));
      VulnerabilityInjectExpectation agentExpectation = new VulnerabilityInjectExpectation();
      agentExpectation.setId("agent-expectation");
      InjectExpectationResult managerResult =
          InjectExpectationResult.builder()
              .sourceId(ExpectationsExpirationManagerConfig.COLLECTOR_ID)
              .sourceType("collector")
              .sourceName("Expectations Expiration Manager")
              .result("Not vulnerable")
              .score(100.0)
              .build();
      agentExpectation.setResults(new ArrayList<>(List.of(managerResult)));
      when(injectExpectationRepository.findAllByInjectAndAsset("inject-id", "asset-id"))
          .thenReturn(List.of(assetExpectation));
      when(injectExpectationRepository.findAllAgentExpectationsByInjectAndAsset(
              "inject-id", "asset-id"))
          .thenReturn(List.of(agentExpectation));

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "asset-id", "parent-id", "ASSETS");

      assertEquals(1, merged.size());
      assertEquals(List.of(nucleiResult), merged.get(0).getResults());
    }

    @Test
    @DisplayName(
        "Vulnerability display merge keeps the expiration manager row when nothing answered")
    void vulnerabilityDisplayMergeKeepsExpirationManagerRowWhenNothingAnswered() {
      // When no platform ever answered, the expiration manager row IS the verdict
      // ("Not vulnerable" by silence) and must stay visible on the asset view.
      VulnerabilityInjectExpectation assetExpectation = new VulnerabilityInjectExpectation();
      assetExpectation.setId("asset-expectation");
      VulnerabilityInjectExpectation agentExpectation = new VulnerabilityInjectExpectation();
      agentExpectation.setId("agent-expectation");
      InjectExpectationResult managerResult =
          InjectExpectationResult.builder()
              .sourceId(ExpectationsExpirationManagerConfig.COLLECTOR_ID)
              .sourceType("collector")
              .sourceName("Expectations Expiration Manager")
              .result("Not vulnerable")
              .score(100.0)
              .build();
      agentExpectation.setResults(new ArrayList<>(List.of(managerResult)));
      when(injectExpectationRepository.findAllByInjectAndAsset("inject-id", "asset-id"))
          .thenReturn(List.of(assetExpectation));
      when(injectExpectationRepository.findAllAgentExpectationsByInjectAndAsset(
              "inject-id", "asset-id"))
          .thenReturn(List.of(agentExpectation));

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "asset-id", "parent-id", "ASSETS");

      assertEquals(1, merged.size());
      assertEquals(List.of(managerResult), merged.get(0).getResults());
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

  @Nested
  @DisplayName("findMergedExpectationsByInjectAndTargetAndTargetType same-type display merge")
  class SameTypeDisplayMergeTests {

    private ManualInjectExpectation manualStep(String id, String name, Double score) {
      ManualInjectExpectation expectation = new ManualInjectExpectation();
      expectation.setId(id);
      expectation.setName(name);
      expectation.setScore(score);
      expectation.setExpectedScore(100.0);
      expectation.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId("phishing-injector")
                      .sourceType("injector")
                      .sourceName("Phishing")
                      .result(score != null && score == 0.0 ? "Compromised" : "No interaction")
                      .score(score)
                      .build())));
      return expectation;
    }

    @Test
    @DisplayName("Merging same-type expectations never mutates the managed entities")
    void sameTypeMergeIsDisplayOnly() {
      // Regression: the three phishing steps are all MANUAL, so the display merge fires. It used
      // to append the sibling rows' results into the FIRST managed entity and overwrite its score
      // with the max - Hibernate then flushed that on commit, so every poll of the results page
      // grew the row's results JSON (until requests exceeded the Hikari leak threshold and
      // exhausted the pool) and flipped a compromised step back to green in the database.
      ManualInjectExpectation opened = manualStep("exp-opened", "Email not opened", 0.0);
      ManualInjectExpectation clicked = manualStep("exp-clicked", "Link not clicked", 100.0);
      ManualInjectExpectation submitted =
          manualStep("exp-submitted", "Credentials not submitted", 100.0);
      when(injectExpectationRepository.findAllByInjectAndTeam("inject-id", "team-id"))
          .thenReturn(List.of(opened, clicked, submitted));

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "team-id", "TEAMS");

      assertEquals(1, merged.size());
      BaseInjectExpectation electedClone = merged.get(0);
      assertNotSame(opened, electedClone);
      assertEquals(3, electedClone.getResults().size());
      // Worst-step verdict: one compromised step keeps the merged human response red.
      assertEquals(0.0, electedClone.getScore());
      // The managed entities are untouched: nothing to flush back to the database.
      assertEquals(1, opened.getResults().size());
      assertEquals(0.0, opened.getScore());
      assertEquals(1, clicked.getResults().size());
      assertEquals(100.0, clicked.getScore());
      assertEquals(1, submitted.getResults().size());
      assertEquals(100.0, submitted.getScore());
    }

    @Test
    @DisplayName("A single expectation per type is returned as-is")
    void singleExpectationPerTypeIsReturnedAsIs() {
      ManualInjectExpectation single = manualStep("exp-single", "Manual validation", null);
      when(injectExpectationRepository.findAllByInjectAndTeam("inject-id", "team-id"))
          .thenReturn(List.of(single));

      List<? extends BaseInjectExpectation> merged =
          injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
              "inject-id", "team-id", "TEAMS");

      assertEquals(List.of(single), merged);
    }
  }

  @Nested
  @DisplayName("findMergedExpectationsByInjectAndTargetAndTargetType for asset groups")
  class AssetGroupSecurityPlatformEnrichmentTests {

    private InjectExpectationResult collectorResult(String result, Double score) {
      return InjectExpectationResult.builder()
          .sourceId("collector-1")
          .sourceType("collector")
          .sourceName("Microsoft Defender")
          .result(result)
          .score(score)
          .build();
    }

    private List<? extends BaseInjectExpectation> merge() {
      return injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
          "inject-id", "group-id", "parent-id", "ASSETS_GROUPS");
    }

    @Test
    @DisplayName("Asset-group expectations mirror their children's security-platform results")
    void assetGroupExpectationsAreEnrichedWithChildrenSecurityPlatformResults() {
      // Regression (second half of #7147): the collector path writes its per-platform results on
      // the agent rows only, so the group synthesis row displayed NO security platform at all
      // while every underlying asset showed e.g. "Microsoft Defender - Not Prevented".
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      DetectionInjectExpectation agentExpectation = new DetectionInjectExpectation();
      agentExpectation.setId("agent-expectation");
      InjectExpectationResult defenderResult = collectorResult("Not Detected", 0.0);
      agentExpectation.setResults(new ArrayList<>(List.of(defenderResult)));
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of(agentExpectation));

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(1, merged.size());
      assertEquals(List.of(defenderResult), merged.get(0).getResults());
      // The enrichment must stay display-only: the persistent entity is untouched.
      assertTrue(groupExpectation.getResults().isEmpty());
    }

    @Test
    @DisplayName("A platform's group row keeps its worst verdict under the default all-assets rule")
    void assetGroupPlatformRowKeepsWorstVerdictUnderAllAssetsRule() {
      // Default validation rule ("all assets must validate"): one missed asset fails the group,
      // so the platform's overall verdict is its worst result across the group's children.
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      groupExpectation.setExpectationGroup(false);
      groupExpectation.setExpectedScore(100.0);
      DetectionInjectExpectation detectedChild = new DetectionInjectExpectation();
      detectedChild.setId("agent-detected");
      detectedChild.setResults(new ArrayList<>(List.of(collectorResult("Detected", 100.0))));
      DetectionInjectExpectation missedChild = new DetectionInjectExpectation();
      missedChild.setId("agent-missed");
      InjectExpectationResult missedResult = collectorResult("Not Detected", 0.0);
      missedChild.setResults(new ArrayList<>(List.of(missedResult)));
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of(detectedChild, missedChild));

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(1, merged.size());
      assertEquals(List.of(missedResult), merged.get(0).getResults());
    }

    @Test
    @DisplayName("A platform's group row keeps its best verdict under the at-least-one rule")
    void assetGroupPlatformRowKeepsBestVerdictUnderAtLeastOneRule() {
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      groupExpectation.setExpectationGroup(true);
      groupExpectation.setExpectedScore(100.0);
      DetectionInjectExpectation detectedChild = new DetectionInjectExpectation();
      detectedChild.setId("agent-detected");
      InjectExpectationResult detectedResult = collectorResult("Detected", 100.0);
      detectedChild.setResults(new ArrayList<>(List.of(detectedResult)));
      DetectionInjectExpectation missedChild = new DetectionInjectExpectation();
      missedChild.setId("agent-missed");
      missedChild.setResults(new ArrayList<>(List.of(collectorResult("Not Detected", 0.0))));
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of(detectedChild, missedChild));

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(1, merged.size());
      assertEquals(List.of(detectedResult), merged.get(0).getResults());
    }

    @Test
    @DisplayName("An answered child result beats a pending one for the same platform")
    void answeredChildResultBeatsPendingOne() {
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      groupExpectation.setExpectationGroup(false);
      DetectionInjectExpectation pendingChild = new DetectionInjectExpectation();
      pendingChild.setId("agent-pending");
      pendingChild.setResults(new ArrayList<>(List.of(collectorResult(null, null))));
      DetectionInjectExpectation answeredChild = new DetectionInjectExpectation();
      answeredChild.setId("agent-answered");
      InjectExpectationResult answeredResult = collectorResult("Not Detected", 0.0);
      answeredChild.setResults(new ArrayList<>(List.of(answeredResult)));
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of(pendingChild, answeredChild));

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(1, merged.size());
      assertEquals(List.of(answeredResult), merged.get(0).getResults());
    }

    @Test
    @DisplayName("A direct result persisted on the group row itself stays visible")
    void directGroupResultStaysVisible() {
      // Assessment injectors (e.g. Nuclei) write their verdict directly on the group row: the
      // display union must keep it next to the children's platforms.
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      InjectExpectationResult directResult =
          InjectExpectationResult.builder()
              .sourceId("nuclei-security-platform")
              .sourceType("security-platform")
              .sourceName("Nuclei")
              .result("Detected")
              .score(100.0)
              .build();
      groupExpectation.setResults(new ArrayList<>(List.of(directResult)));
      DetectionInjectExpectation agentExpectation = new DetectionInjectExpectation();
      agentExpectation.setId("agent-expectation");
      InjectExpectationResult defenderResult = collectorResult("Not Detected", 0.0);
      agentExpectation.setResults(new ArrayList<>(List.of(defenderResult)));
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of(agentExpectation));

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(1, merged.size());
      assertEquals(List.of(defenderResult, directResult), merged.get(0).getResults());
    }

    @Test
    @DisplayName("Asset groups without children rows are returned unchanged")
    void assetGroupsWithoutChildrenAreReturnedUnchanged() {
      DetectionInjectExpectation groupExpectation = new DetectionInjectExpectation();
      groupExpectation.setId("group-expectation");
      when(injectExpectationRepository.findAllByInjectAndAssetGroup("inject-id", "group-id"))
          .thenReturn(List.of(groupExpectation));
      when(injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
              "inject-id", "group-id"))
          .thenReturn(List.of());

      List<? extends BaseInjectExpectation> merged = merge();

      assertEquals(List.of(groupExpectation), merged);
    }
  }
}
