package io.openaev.rest.inject_expectation;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID;
import static io.openaev.utils.fixtures.ExpectationFixture.*;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.inject.form.InjectExpectationUpdateFromCollectorInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.LogService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.audit-logs.transports=console",
      "openaev.audit-logs.halt-on-failure=false"
    })
public class ExpectationsExpirationManagerServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";

  public static final long EXPIRATION_TIME_1_s = 1L;

  @Autowired private EntityManager em;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private ExpectationsExpirationManagerService expectationsExpirationManagerService;
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private CollectorTypeRepository collectorTypeRepository;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  // Saved entities for test setup
  private Injector savedInjector;
  private InjectorContract savedInjectorContract;
  private AssetGroup savedAssetGroup;
  private Endpoint savedEndpoint;
  private Endpoint savedAgentlessAsset;
  private Agent savedAgent1;
  private Agent savedAgent2;
  private Inject savedInject;
  private Collector savedEDRCollector;

  @BeforeEach
  void beforeEach() throws Exception {
    reset(auditLogger);
    reset(logService);
    reset(auditLogTransportDispatcherUtils);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(logService).isEnabled();

    // Register the builtin collector for the test tenant (builtins are only registered
    // for tenants that exist at startup, not for the test tenant created by @WithMockUser)
    expectationsExpirationManagerJob.registerForTenant(Tenant.DEFAULT_TENANT_UUID);

    // Use the builtin injector if already registered, otherwise create it
    savedInjector =
        injectorRepository
            .findByIdAndTenantId(OPENAEV_INJECTOR_ID, Tenant.DEFAULT_TENANT_UUID)
            .orElseGet(
                () ->
                    injectorRepository.save(
                        InjectorFixture.createInjector(
                            OPENAEV_INJECTOR_ID, "OpenAEV Implant", "openaev_implant")));

    InjectorContract injectorContract;
    try {
      injectorContract =
          InjectorContractFixture.createInjectorContract(java.util.Map.of("en", INJECTION_NAME));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    injectorContract.addInjector(savedInjector);
    savedInjectorContract = injectorContractRepository.save(injectorContract);
    savedInjector.linkContract(savedInjectorContract);
    injectorRepository.save(savedInjector);

    // -- Targets --
    savedEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
    savedAgentlessAsset = endpointRepository.save(EndpointFixture.createEndpoint());
    savedAgent1 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external01"));
    savedAgent2 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external02"));
    savedAssetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "asset group name", List.of(savedEndpoint)));

    // -- Inject --
    savedInject =
        injectRepository.save(
            InjectFixture.createTechnicalInjectWithAssetGroup(
                savedInjectorContract, INJECTION_NAME, savedAssetGroup));

    CollectorType collectorType1 = new CollectorType(UUID.randomUUID().toString());
    collectorType1.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    collectorTypeRepository.save(collectorType1);
    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    collector.setName("collector-name");
    collector.setType(collectorType1.getName());
    collector.setCollectorType(collectorType1);
    collector.setExternal(true);
    collector.setSecurityPlatform(
        securityPlatformRepository.save(
            SecurityPlatformFixture.createDefault(
                "collector-security-platform-edr-1",
                SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())));
    savedEDRCollector = collectorRepository.save(collector);

    em.flush();
    em.clear();
  }

  @Nested
  @DisplayName("Update injectExpectations with expectationsExpirationManagerService")
  class ComputeExpectationsWithExpectationExpiredManagerService {

    @Test
    @DisplayName("Expired agent expectations emit automatic expectation-result audit events")
    void given_expiredAgentExpectations_should_emitAutomaticExpectationResultAuditEvents() {
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      List<String> agentExpectationIds =
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
              .stream()
              .map(BaseInjectExpectation::getId)
              .toList();
      List<String> secondAgentExpectationIds =
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
              .stream()
              .map(BaseInjectExpectation::getId)
              .toList();
      List<String> allAgentExpectationIds = new ArrayList<>(agentExpectationIds);
      allAgentExpectationIds.addAll(secondAgentExpectationIds);

      expireExpectationsInDbByInjectId(savedInject.getId());
      reset(auditLogTransportDispatcherUtils);

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // Assert
      List<LogEvent> expectationEvents =
          captureExpectationResultEvents(allAgentExpectationIds.size());
      List<String> loggedIds =
          expectationEvents.stream()
              .map(event -> extractExpectationId(event))
              .filter(java.util.Objects::nonNull)
              .toList();
      assertThat(loggedIds).containsAll(allAgentExpectationIds);
      assertThat(expectationEvents)
          .allSatisfy(
              event -> {
                assertThat(event.getEventScope()).isEqualTo("expectation_result");
                assertThat(extractContextValue(event, "source_type")).isEqualTo("collector");
              });
    }

    @Test
    @DisplayName("Directly answerable expired leaf emits automatic expectation-result audit event")
    void given_expiredDirectLeaf_should_emitAutomaticExpectationResultAuditEvent() {
      // Arrange: a plain (non-endpoint) asset never has agent children, so its expectation IS
      // the leaf and is answered directly by the expiration manager, with no parent-recompute
      // involved. Endpoints can't be used here: as soon as they carry active agents (as
      // savedEndpoint does), their asset-level expectation becomes a parent of those agent
      // expectations instead of a leaf.
      Asset leafAsset = assetRepository.save(AssetFixture.createDefaultAsset("leaf asset"));
      AssetGroup leafAssetGroup =
          assetGroupRepository.save(
              AssetGroupFixture.createAssetGroupWithAssets("leaf asset group", List.of(leafAsset)));
      savedInject.setAssetGroups(List.of(leafAssetGroup));
      injectRepository.save(savedInject);

      ExecutableInject executableInject =
          new ExecutableInject(
              false,
              true,
              savedInject,
              emptyList(),
              emptyList(),
              List.of(leafAssetGroup),
              emptyList(),
              emptyList());
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      String assetExpectationId =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), leafAsset.getId())
              .getFirst()
              .getId();

      expireExpectationsInDbByInjectId(savedInject.getId());
      reset(auditLogTransportDispatcherUtils);

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // Assert
      List<LogEvent> expectationEvents = captureExpectationResultEvents(1);
      assertThat(expectationEvents.stream().map(event -> extractExpectationId(event)).toList())
          .contains(assetExpectationId);
      assertThat(expectationEvents)
          .anySatisfy(
              event -> {
                assertThat(extractExpectationId(event)).isEqualTo(assetExpectationId);
                assertThat(extractContextValue(event, "source_type")).isEqualTo("collector");
              });
    }

    @Test
    @DisplayName("Recomputed expired parents emit automatic expectation-result audit events")
    void given_recomputedExpiredParents_should_emitAutomaticExpectationResultAuditEvents() {
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      // The collector answers the first agent only: without expectation group, the asset and the
      // asset group stay pending until the second agent expires
      injectExpectationService.updateInjectExpectation(
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
              .getFirst()
              .getId(),
          InjectExpectationUpdateFromCollectorInput.builder()
              .collectorId(savedEDRCollector.getId())
              .result("Detected")
              .isSuccess(true)
              .build());
      em.flush();
      em.clear();

      BaseInjectExpectation parentAssetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      BaseInjectExpectation parentGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assertNull(parentAssetExpectation.getScore());
      assertNull(parentGroupExpectation.getScore());
      String parentAssetExpectationId = parentAssetExpectation.getId();
      String parentGroupExpectationId = parentGroupExpectation.getId();

      expireExpectationsInDbByInjectId(savedInject.getId());
      reset(auditLogTransportDispatcherUtils);

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // Assert: the run concludes the second agent, then derives the asset and the asset group
      // from it, so both parents are recomputed and logged
      List<LogEvent> expectationEvents = captureExpectationResultEvents(3);
      List<String> loggedIds =
          expectationEvents.stream()
              .map(event -> extractExpectationId(event))
              .filter(java.util.Objects::nonNull)
              .toList();
      assertThat(loggedIds).contains(parentAssetExpectationId, parentGroupExpectationId);
      assertThat(expectationEvents)
          .anySatisfy(
              event -> {
                assertThat(extractExpectationId(event)).isEqualTo(parentAssetExpectationId);
              });
      assertThat(expectationEvents)
          .anySatisfy(
              event -> {
                assertThat(extractExpectationId(event)).isEqualTo(parentGroupExpectationId);
              });
    }

    @Test
    @DisplayName("All injectExpectations are expired")
    void allExpectationAreExpired() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      // -- VERIFY --
      // Agent Expectation
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("One injectExpectations is already filled")
    void OneExpectationIsAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      detectionExpectation.setExpectationGroup(true);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      // Update one expectation from one agent with source collector-id
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      BaseInjectExpectation ie = injectExpectations.getFirst();
      ie.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId("collector-id")
                      .sourceName("collector-name")
                      .sourceType("collector-type")
                      .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                      .result("result")
                      .date(Instant.now())
                      .sourceAssetId(UUID.randomUUID().toString())
                      .score(50.0)
                      .build())));
      ie.setScore(50.0);

      injectExpectationRepository.save(ie);

      // -- VERIFY --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(50.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      injectExpectations = injectExpectationRepository.findAllByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(50.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset
      // There is two agents linked to this asset, so the result should be as expected score
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(detectionExpectation.getScore(), injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(detectionExpectation.getScore(), injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("The agent expectations are already filled")
    void agentExpectationsAreAlreadyFilled() {
      // Arrange
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      detectionExpectation.setExpectationGroup(true);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");
      em.flush();
      em.clear();

      // The collector answers both agents through the collector path, which rolls the verdict up
      // to the asset and the asset group
      InjectExpectationUpdateFromCollectorInput input =
          InjectExpectationUpdateFromCollectorInput.builder()
              .collectorId(savedEDRCollector.getId())
              .result("Detected")
              .isSuccess(true)
              .build();
      List.of(savedAgent1, savedAgent2)
          .forEach(
              agent ->
                  injectExpectationService.updateInjectExpectation(
                      injectExpectationRepository
                          .findAllByInjectAndAgent(savedInject.getId(), agent.getId())
                          .getFirst()
                          .getId(),
                      input));
      em.flush();
      em.clear();

      // The parents are concluded before the expiration manager runs
      assertEquals(
          detectionExpectation.getScore(),
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst()
              .getScore());
      assertEquals(
          detectionExpectation.getScore(),
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst()
              .getScore());
      expireExpectationsInDbByInjectId(savedInject.getId());

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());
      em.flush();
      em.clear();

      // Assert
      // Every verdict is kept: the agents, the asset and the asset group are not force-failed
      for (Agent agent : List.of(savedAgent1, savedAgent2)) {
        BaseInjectExpectation agentExpectation =
            injectExpectationRepository
                .findAllByInjectAndAgent(savedInject.getId(), agent.getId())
                .getFirst();
        assertEquals(detectionExpectation.getScore(), agentExpectation.getScore());
        assertEquals(
            BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, agentExpectation.getResponse());
      }
      BaseInjectExpectation assetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      assertEquals(detectionExpectation.getScore(), assetExpectation.getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, assetExpectation.getResponse());
      BaseInjectExpectation assetGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assertEquals(detectionExpectation.getScore(), assetGroupExpectation.getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, assetGroupExpectation.getResponse());
    }

    @Test
    @DisplayName(
        "Security-platform green agents keep the asset and asset group green after expiration")
    void expirationDoesNotOverrideSecurityPlatformGreenResults() {
      // Regression: the agent was answered PREVENTED/DETECTED (green) by a security platform
      // (e.g. Microsoft Defender) while the asset / asset group parent scores were still pending
      // when the expiration manager ran. The manager used to force-fail the parents with an
      // "Expired" result, permanently showing "Not prevented"/"Not detected" on the asset while
      // its only agent showed green, corrupting the verdicts and all statistics built on them.
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();

      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(EXPIRATION_TIME_1_s);
      detectionExpectation.setExpectationGroup(true);
      detectionExpectation.setScore(100.0);
      Expectation preventionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION, "Detection Expectation");
      preventionExpectation.setExpirationTime(EXPIRATION_TIME_1_s);
      preventionExpectation.setExpectationGroup(true);
      preventionExpectation.setScore(100.0);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation, preventionExpectation), "implantType");

      em.flush();
      em.clear();

      // The security platform answers the first agent green on both prevention and detection,
      // through the security platform path, which rolls the verdict up to the asset and the asset
      // group. The second agent stays pending
      SecurityPlatform defender =
          securityPlatformRepository.save(
              SecurityPlatformFixture.createDefault(
                  "Microsoft Defender", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name()));
      InjectExpectationUpdateFromCollectorInput input =
          new InjectExpectationUpdateFromCollectorInput();
      input.setIsSuccess(true);
      input.setResult("Prevented");
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(2, agentExpectations.size());
      agentExpectations.forEach(
          expectation ->
              injectExpectationService.updateInjectExpectationFromSecurityPlatform(
                  expectation.getId(), input, defender));
      expireExpectationsInDbByInjectId(savedInject.getId());

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());
      em.flush();
      em.clear();

      // Assert
      // The first agent keeps its green verdict, the second one is expired
      injectExpectationRepository
          .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
          .forEach(
              expectation -> {
                assertEquals(100.0, expectation.getScore());
                assertEquals(
                    BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, expectation.getResponse());
              });
      injectExpectationRepository
          .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
          .forEach(
              expectation ->
                  assertEquals(
                      BaseInjectExpectation.EXPECTATION_STATUS.FAILED, expectation.getResponse()));
      // The asset and the asset group stay green for both types (expectation group: one green
      // agent is enough)
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(2, assetExpectations.size());
      assetExpectations.forEach(
          expectation -> {
            assertEquals(100.0, expectation.getScore());
            assertEquals(
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, expectation.getResponse());
          });
      List<BaseInjectExpectation> assetGroupExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(2, assetGroupExpectations.size());
      assetGroupExpectations.forEach(
          expectation -> {
            assertEquals(100.0, expectation.getScore());
            assertEquals(
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, expectation.getResponse());
          });
    }

    @Test
    @DisplayName(
        "Parent expectations expiring before their agents stay pending, never failed nor audited")
    void parentExpirationBeforeAgentsStaysPending() {
      // Regression companion: parent asset / asset group expectations reaching their expiration
      // while their agent children are still legitimately pending (agents have a longer
      // expiration window) must stay pending - the verdict belongs to the children.
      long agentExpirationSeconds = 3600L;
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(agentExpirationSeconds);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      // Backdate ONLY the parent (asset / asset group) expectations so they are expired while the
      // agent expectations remain within their window
      em.createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_created_at = :past, inject_expiration_time = 1 WHERE inject_id = :injectId AND agent_id IS NULL")
          .setParameter("past", Instant.now().minus(2, ChronoUnit.HOURS))
          .setParameter("injectId", savedInject.getId())
          .executeUpdate();
      em.flush();
      em.clear();
      reset(auditLogTransportDispatcherUtils);

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT -- no expectation is concluded, so no automatic result is audited
      verify(auditLogTransportDispatcherUtils, after(2000).never())
          .dispatch(
              argThat((LogEvent event) -> "expectation_result".equals(event.getEventScope())),
              any());
      // everything still pending: agents within their window, parents wait for them
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("AgentAsset expectations")
    void assetExpectationWithoutAgentExpectationsLinked() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithAgentlessAssetTarget();
      Expectation detectionExpectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      detectionExpectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(detectionExpectation), "implantType");

      em.flush();
      em.clear();

      // -- VERIFY --
      // Asset
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Asset
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
      // Asset Group
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, injectExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("Vulnerability expectation with an agent gets expired")
    void vulnerableExpectationIsExpired() {
      // -- PREPARE --
      // Build and save an expectation for an asset and one agent
      ExecutableInject executableInject = newExecutableInjectWithTargets();

      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Detection Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      // -- VERIFY --
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(null, injectExpectations.getFirst().getScore());

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(expectation.getScore(), injectExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          injectExpectations.getFirst().getResponse());
    }

    @Test
    @DisplayName("A direct VULNERABLE verdict on the asset survives the agent expiration rollup")
    void directVulnerableVerdictSurvivesAgentExpiration() {
      // Regression: an assessment injector (e.g. Nuclei, agentless execution) wrote VULNERABLE
      // directly on the asset-level expectation, while the agent-level children stayed untouched.
      // When the expiration manager later expired the agents to the default "Not vulnerable", the
      // children rollup overwrote the proven vulnerable verdict with "Not vulnerable / 100" and
      // stamped a contradicting expiration result on the asset row.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      // The assessment injector answers the ASSET row directly: proven vulnerable
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      BaseInjectExpectation assetExpectation = assetExpectations.getFirst();
      assetExpectation.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId("nuclei-security-platform")
                      .sourceName("Nuclei")
                      .sourceType("security-platform")
                      .sourcePlatform(
                          SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
                      .result("Vulnerable")
                      .date(Instant.now())
                      .sourceAssetId(UUID.randomUUID().toString())
                      .score(0.0)
                      .build())));
      assetExpectation.setScore(0.0);
      injectExpectationRepository.save(assetExpectation);

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Agent child: expired to the vulnerability default "Not vulnerable"
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(expectation.getScore(), agentExpectations.getFirst().getScore());
      // Asset: the proven VULNERABLE verdict survives the children rollup
      assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, assetExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.FAILED,
          assetExpectations.getFirst().getResponse());
      // And no contradicting "Not vulnerable" expiration row was stamped on the asset
      assertTrue(
          assetExpectations.getFirst().getResults().stream()
              .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
    }

    private InjectExpectationResult makeFailedNucleiResult() {
      return InjectExpectationResult.builder()
          .sourceId("nuclei-security-platform")
          .sourceName("Nuclei")
          .sourceType("security-platform")
          .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
          .result("Vulnerable")
          .date(Instant.now())
          .sourceAssetId(UUID.randomUUID().toString())
          .score(0.0)
          .build();
    }

    @Test
    @DisplayName(
        "A direct VULNERABLE verdict on the asset group is not contradicted by an expiration stamp")
    void directVulnerableVerdictOnAssetGroupIsNotContradictedByExpirationStamp() {
      // Regression: an assessment injector (e.g. Nuclei) answered BOTH the asset and the asset
      // group rows with VULNERABLE, while the agent-level children stayed untouched (an agentless
      // scanner never fills them). When the expiration manager later expired the agents to the
      // default "Not vulnerable", the asset group rollup correctly concluded VULNERABLE - but the
      // propagation still stamped the triggering agent result (an expiration manager
      // "Not vulnerable", success polarity) onto the group row, displaying a contradictory entry
      // next to the genuine platform verdict and planting a success score in the results list.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      // The assessment injector answers the ASSET and ASSET GROUP rows directly: proven vulnerable
      BaseInjectExpectation assetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      assetExpectation.setResults(new ArrayList<>(List.of(makeFailedNucleiResult())));
      assetExpectation.setScore(0.0);
      BaseInjectExpectation assetGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assetGroupExpectation.setResults(new ArrayList<>(List.of(makeFailedNucleiResult())));
      assetGroupExpectation.setScore(0.0);
      injectExpectationRepository.saveAll(List.of(assetExpectation, assetGroupExpectation));

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Agent child: expired to the vulnerability default "Not vulnerable"
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(expectation.getScore(), agentExpectations.getFirst().getScore());
      // Asset: the proven VULNERABLE verdict survives, no contradicting stamp
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(0.0, assetExpectations.getFirst().getScore());
      assertTrue(
          assetExpectations.getFirst().getResults().stream()
              .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
      // Asset group: the proven VULNERABLE verdict survives, and NO contradicting
      // "Not vulnerable" expiration manager row was stamped next to the Nuclei verdict
      List<BaseInjectExpectation> assetGroupExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(0.0, assetGroupExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.FAILED,
          assetGroupExpectations.getFirst().getResponse());
      assertTrue(
          assetGroupExpectations.getFirst().getResults().stream()
              .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
    }

    private InjectExpectationResult makeSuccessfulNucleiResult() {
      return InjectExpectationResult.builder()
          .sourceId("nuclei-security-platform")
          .sourceName("Nuclei")
          .sourceType("security-platform")
          .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
          .result("Not vulnerable")
          .date(Instant.now())
          .sourceAssetId(UUID.randomUUID().toString())
          .score(100.0)
          .build();
    }

    @Test
    @DisplayName(
        "The expiration manager never stamps a vulnerability row a platform already answered")
    void expirationManagerDoesNotStampVulnerabilityRowsAlreadyAnswered() {
      // Regression: Nuclei answered "Not vulnerable" directly on the asset and asset-group rows of
      // endpoints that happen to run an agent. When the agent children (which an agentless scanner
      // never fills) later expired, the propagation stamped a redundant "Expectations Expiration
      // Manager - Not vulnerable" row next to the genuine scan verdict on every answered parent.
      // The expiration default is the absence-of-signal fallback: it has nothing to add to a row a
      // real platform already answered.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      expectation.setExpectationGroup(true);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      // The scanner answers the ASSET and ASSET GROUP rows directly: nothing found
      BaseInjectExpectation assetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      assetExpectation.setResults(new ArrayList<>(List.of(makeSuccessfulNucleiResult())));
      assetExpectation.setScore(100.0);
      BaseInjectExpectation assetGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assetGroupExpectation.setResults(new ArrayList<>(List.of(makeSuccessfulNucleiResult())));
      assetGroupExpectation.setScore(100.0);
      injectExpectationRepository.saveAll(List.of(assetExpectation, assetGroupExpectation));

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      // Agent child: expired to the vulnerability default "Not vulnerable" by the manager
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(expectation.getScore(), agentExpectations.getFirst().getScore());
      assertTrue(
          agentExpectations.getFirst().getResults().stream()
              .anyMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
      // Asset: verdict unchanged, and NO redundant manager row next to the Nuclei answer
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(expectation.getScore(), assetExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          assetExpectations.getFirst().getResponse());
      assertTrue(
          assetExpectations.getFirst().getResults().stream()
              .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
      // Asset group: verdict unchanged, and NO redundant manager row either
      List<BaseInjectExpectation> assetGroupExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(expectation.getScore(), assetGroupExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          assetGroupExpectations.getFirst().getResponse());
      assertTrue(
          assetGroupExpectations.getFirst().getResults().stream()
              .noneMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
    }

    @Test
    @DisplayName("A direct NOT VULNERABLE verdict concludes the asset without waiting for expiry")
    void directNotVulnerableVerdictConcludesImmediately() {
      // Regression: a scanner assessing an endpoint that happens to run an agent answered "Not
      // vulnerable" on the asset row, but the row deferred to its agent children - which an
      // agentless injector never fills - and stayed PENDING until the expiration manager fired
      // minutes later, contradicting the verdict already displayed on the row.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      SecurityPlatform nuclei =
          securityPlatformRepository.save(
              SecurityPlatformFixture.createDefault("Nuclei", "VULNERABILITY_SCANNER"));
      String assetExpectationId =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst()
              .getId();

      // -- EXECUTE --
      // The scanner answers the ASSET row directly: nothing found.
      InjectExpectationUpdateFromCollectorInput input =
          new InjectExpectationUpdateFromCollectorInput();
      input.setIsSuccess(true);
      input.setResult("Not vulnerable");
      injectExpectationService.updateInjectExpectationFromSecurityPlatform(
          assetExpectationId, input, nuclei);

      em.flush();
      em.clear();

      // -- ASSERT --
      // Asset: concluded straight away, no expiration manager run involved.
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(expectation.getScore(), assetExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          assetExpectations.getFirst().getResponse());
      // The agent child is untouched: the scanner knows nothing about agents.
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertNull(agentExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("Expiring an asset keeps the scanner verdict of an unexpired sibling asset")
    void expirationKeepsScannerVerdictOfUnexpiredSibling() {
      // Regression: the rollup of an expiration recomputed every parent of the inject, so expiring
      // asset A reset the "Not vulnerable" verdict a scanner wrote directly on sibling asset B,
      // whose agent children are still pending. Only the ancestors of the expired leaves (asset A
      // and its group) must be recomputed.
      Endpoint siblingEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
      Agent siblingAgent =
          agentRepository.save(AgentFixture.createAgent(siblingEndpoint, "sibling"));
      AssetGroup assetGroup =
          assetGroupRepository.save(
              AssetGroupFixture.createAssetGroupWithAssets(
                  "asset group with siblings", List.of(savedEndpoint, siblingEndpoint)));
      savedInject.setAssetGroups(List.of(assetGroup));
      injectRepository.save(savedInject);
      // The active agents are read from the endpoint: reload it so it carries its new agent
      em.flush();
      em.clear();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      // Long enough for the sibling agent not to expire during the test
      expectation.setExpirationTime(DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
      injectExpectationService.computeAndSaveExpectations(
          newExecutableInjectWithTargets(), List.of(expectation), "implantType");
      em.flush();
      em.clear();

      // The scanner answers the sibling ASSET row directly: nothing found
      SecurityPlatform nuclei =
          securityPlatformRepository.save(
              SecurityPlatformFixture.createDefault("Nuclei", "VULNERABILITY_SCANNER"));
      InjectExpectationUpdateFromCollectorInput input =
          new InjectExpectationUpdateFromCollectorInput();
      input.setIsSuccess(true);
      input.setResult("Not vulnerable");
      injectExpectationService.updateInjectExpectationFromSecurityPlatform(
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), siblingEndpoint.getId())
              .getFirst()
              .getId(),
          input,
          nuclei);
      em.flush();
      // Only the agents of asset A are expired
      em.createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_created_at = :past WHERE inject_id = :injectId AND agent_id IN (:agentIds)")
          .setParameter("past", Instant.now().minus(1, ChronoUnit.DAYS))
          .setParameter("injectId", savedInject.getId())
          .setParameter("agentIds", List.of(savedAgent1.getId(), savedAgent2.getId()))
          .executeUpdate();
      em.flush();
      em.clear();

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());
      em.flush();
      em.clear();

      // -- ASSERT --
      // Sibling asset B keeps the scanner verdict, its agent is still pending
      assertEquals(
          expectation.getScore(),
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), siblingEndpoint.getId())
              .getFirst()
              .getScore());
      assertNull(
          injectExpectationRepository
              .findAllByInjectAndAgent(savedInject.getId(), siblingAgent.getId())
              .getFirst()
              .getScore());
      // Asset A is derived from its expired agents, and the group from both assets
      assertEquals(
          expectation.getScore(),
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst()
              .getScore());
      assertEquals(
          expectation.getScore(),
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), assetGroup.getId())
              .getFirst()
              .getScore());
    }

    @Test
    @DisplayName("Expiring the agents of two assets in one run derives both assets and their group")
    void given_expiredAgentsOfTwoAssets_should_deriveBothAssetsAndTheirGroup() {
      // Arrange
      // Regression: the rollup of a batch kept one expired leaf per inject and type, so with a
      // targeted recompute only the ancestors of that leaf were derived. The other asset stayed
      // pending forever, as its leaves were scored and never selected again.
      Endpoint secondEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
      Agent secondAgent = agentRepository.save(AgentFixture.createAgent(secondEndpoint, "second"));
      AssetGroup assetGroup =
          assetGroupRepository.save(
              AssetGroupFixture.createAssetGroupWithAssets(
                  "asset group with two assets", List.of(savedEndpoint, secondEndpoint)));
      // The expectations are resolved from the inject targets: the first asset is targeted both
      // directly and through the group
      savedInject.setAssets(new ArrayList<>(List.of(savedEndpoint)));
      savedInject.setAssetGroups(List.of(assetGroup));
      injectRepository.save(savedInject);
      // The active agents are read from the endpoint: reload it so it carries its new agent
      em.flush();
      em.clear();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.DETECTION, "Detection Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          new ExecutableInject(
              false,
              true,
              savedInject,
              emptyList(),
              List.of(savedEndpoint),
              List.of(assetGroup),
              emptyList(),
              emptyList()),
          List.of(expectation),
          "implantType");
      expireExpectationsInDbByInjectId(savedInject.getId());

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());
      em.flush();
      em.clear();

      // Assert
      // Every row of both assets (targeted directly and through the group) and the group are
      // derived from the expired agents
      assertThat(
              injectExpectationRepository.findAllByInjectAndAgent(
                  savedInject.getId(), secondAgent.getId()))
          .isNotEmpty()
          .allSatisfy(agent -> assertNotNull(agent.getScore()));
      assertThat(
              injectExpectationRepository.findAllByInjectAndAsset(
                  savedInject.getId(), savedEndpoint.getId()))
          .hasSize(2)
          .allSatisfy(asset -> assertNotNull(asset.getScore()));
      assertThat(
              injectExpectationRepository.findAllByInjectAndAsset(
                  savedInject.getId(), secondEndpoint.getId()))
          .isNotEmpty()
          .allSatisfy(asset -> assertNotNull(asset.getScore()));
      assertNotNull(
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), assetGroup.getId())
              .getFirst()
              .getScore());
    }

    @Test
    @DisplayName("An agentless vulnerability leaf expires to Not vulnerable, not to failed")
    void agentlessVulnerabilityLeafExpiresToNotVulnerable() {
      // For VULNERABILITY, silence means "nothing found": an agentless asset expectation that no
      // scanner ever answered must expire to the success default (Not vulnerable), matching the
      // agent-level expiration behavior - not to a failed "Vulnerable" verdict.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          createExpectation(
              BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY, "Vulnerability Expectation");
      expectation.setExpirationTime(EXPIRATION_TIME_1_s);
      injectExpectationService.computeAndSaveExpectations(
          executableInject, List.of(expectation), "implantType");

      em.flush();
      em.clear();

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(expectation.getScore(), assetExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          assetExpectations.getFirst().getResponse());
    }
  }

  // -- PRIVATE HELPERS --

  private List<LogEvent> captureExpectationResultEvents(int expectedMinimumEvents) {
    // Audit dispatch runs on AuditLogger's own executor (CompletableFuture.supplyAsync), not on
    // the calling thread: verify() must wait for the full expected count, not just one
    // invocation, or a slower/loaded CI runner can race ahead and capture only the events that
    // happened to complete first.
    ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
    verify(auditLogTransportDispatcherUtils, timeout(5000).atLeast(expectedMinimumEvents))
        .dispatch(eventCaptor.capture(), any());
    List<LogEvent> expectationEvents =
        eventCaptor.getAllValues().stream()
            .filter(event -> "expectation_result".equals(event.getEventScope()))
            .toList();
    assertThat(expectationEvents).hasSizeGreaterThanOrEqualTo(expectedMinimumEvents);
    return expectationEvents;
  }

  private String extractExpectationId(LogEvent event) {
    Object value =
        event.getContextData() != null ? event.getContextData().get("expectation_id") : null;
    return value != null ? String.valueOf(value) : null;
  }

  private String extractContextValue(LogEvent event, String key) {
    Map<String, Object> contextData = event.getContextData();
    if (contextData == null || !contextData.containsKey(key) || contextData.get(key) == null) {
      return null;
    }
    return String.valueOf(contextData.get(key));
  }

  /** Backdates all expectations for the given inject so the SQL expiration filter picks them up. */
  private void expireExpectationsInDbByInjectId(String injectId) {
    em.flush();
    em.createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_created_at = :past WHERE inject_id = :injectId")
        .setParameter("past", Instant.now().minus(1, ChronoUnit.DAYS))
        .setParameter("injectId", injectId)
        .executeUpdate();
    em.flush();
    em.clear();
  }

  private ExecutableInject newExecutableInjectWithTargets() {
    return new ExecutableInject(
        false,
        true,
        savedInject,
        emptyList(),
        List.of(savedEndpoint),
        List.of(savedAssetGroup),
        emptyList(),
        emptyList());
  }

  private ExecutableInject newExecutableInjectWithAgentlessAssetTarget() {
    return new ExecutableInject(
        false,
        true,
        savedInject,
        emptyList(),
        List.of(savedAgentlessAsset),
        List.of(savedAssetGroup),
        emptyList(),
        emptyList());
  }
}
