package io.openaev.rest.inject_expectation;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAsset;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAssetGroup;
import static io.openaev.utils.fixtures.ExpectationFixture.*;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.Expectation;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
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

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  // Saved entities for test setup
  private Injector savedInjector;
  private InjectorContract savedInjectorContract;
  private AssetGroup savedAssetGroup;
  private Endpoint savedEndpoint;
  private Agent savedAgent1;
  private Agent savedAgent2;
  private Inject savedInject;

  @BeforeEach
  void beforeEach() throws Exception {
    reset(auditLogger);
    reset(logService);
    reset(auditLogTransportDispatcherUtils);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(logService).isEnabled();

    // Register the builtin collector for the test tenant (builtins are only registered
    // for tenants that exist at startup, not for the test tenant created by @WithMockUser)
    expectationsExpirationManagerJob.registerForTenant(TenantContext.getCurrentTenant());

    // Use the builtin injector if already registered, otherwise create it
    savedInjector =
        injectorRepository
            .findByIdAndTenantId(OPENAEV_INJECTOR_ID, TenantContext.getCurrentTenant())
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
  }

  @Nested
  @DisplayName("Update injectExpectations with expectationsExpirationManagerService")
  class ComputeExpectationsWithExpectationExpiredManagerService {

    @Test
    @DisplayName("Expired agent expectations emit automatic expectation-result audit events")
    void given_expiredAgentExpectations_should_emitAutomaticExpectationResultAuditEvents() {
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

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
                assertThat(extractContextValue(event, "source_type")).isEqualTo("automatic");
              });
    }

    @Test
    @DisplayName("Directly answerable expired leaf emits automatic expectation-result audit event")
    void given_expiredDirectLeaf_should_emitAutomaticExpectationResultAuditEvent() {
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              null,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, List.of(expectation));

      em.flush();
      em.clear();

      String assetExpectationId =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
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
                assertThat(extractContextValue(event, "source_type")).isEqualTo("automatic");
              });
    }

    @Test
    @DisplayName("Recomputed expired parents emit automatic expectation-result audit events")
    void given_recomputedExpiredParents_should_emitAutomaticExpectationResultAuditEvents() {
      // Arrange
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      em.flush();
      em.clear();

      List<BaseInjectExpectation> agentExpectations =
          List.of(
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                  .getFirst(),
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
                  .getFirst());
      agentExpectations.forEach(
          e -> {
            e.setResults(
                List.of(
                    InjectExpectationResult.builder()
                        .sourceId("collector-id")
                        .sourceName("collector-name")
                        .sourceType("collector-type")
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .result("result")
                        .sourceAssetId(UUID.randomUUID().toString())
                        .score(100.0)
                        .build()));
            e.setScore(100.0);
          });
      injectExpectationRepository.saveAll(agentExpectations);

      String parentAssetExpectationId =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst()
              .getId();
      String parentGroupExpectationId =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst()
              .getId();

      expireExpectationsInDbByInjectId(savedInject.getId());
      reset(auditLogTransportDispatcherUtils);

      // Act
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // Assert
      List<LogEvent> expectationEvents = captureExpectationResultEvents(2);
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
                assertThat(extractContextValue(event, "source_type")).isEqualTo("automatic");
              });
      assertThat(expectationEvents)
          .anySatisfy(
              event -> {
                assertThat(extractExpectationId(event)).isEqualTo(parentGroupExpectationId);
                assertThat(extractContextValue(event, "source_type")).isEqualTo("automatic");
              });
    }

    @Test
    @DisplayName("All injectExpectations are expired")
    void allExpectationAreExpired() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      detectionExpectations.add(
          createTechnicalDetectionExpectationForAsset(savedEndpoint, null, EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

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
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      em.flush();
      em.clear();

      // Update one expectation from one agent with source collector-id
      List<BaseInjectExpectation> injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());

      BaseInjectExpectation ie = injectExpectations.getFirst();
      ie.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId("collector-id")
                  .sourceName("collector-name")
                  .sourceType("collector-type")
                  .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                  .result("result")
                  .sourceAssetId(UUID.randomUUID().toString())
                  .score(50.0)
                  .build()));
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
    @DisplayName("The agent expectations are already filled")
    void agentExpectationsAreAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents

      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      em.flush();
      em.clear();

      // Update agent expectations with source collector-id
      List<BaseInjectExpectation> injectExpectations =
          List.of(
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                  .getFirst(),
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
                  .getFirst());

      injectExpectations.forEach(
          BaseInjectExpectation -> {
            BaseInjectExpectation.setResults(
                List.of(
                    InjectExpectationResult.builder()
                        .sourceId("collector-id")
                        .sourceName("collector-name")
                        .sourceType("collector-type")
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .result("result")
                        .sourceAssetId(UUID.randomUUID().toString())
                        .score(100.0)
                        .build()));
            BaseInjectExpectation.setScore(100.0);
          });

      injectExpectationRepository.saveAll(injectExpectations);

      // -- VERIFY --
      // Agent Expectation
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
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
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent2.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      // Asset: rolled up from its (green) agents by the expiration manager, NOT force-failed
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          injectExpectations.getFirst().getResponse());
      // Asset Group: rolled up from its (green) assets by the expiration manager
      injectExpectations =
          injectExpectationRepository.findAllByInjectAndAssetGroup(
              savedInject.getId(), savedAssetGroup.getId());
      assertEquals(100.0, injectExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          injectExpectations.getFirst().getResponse());
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
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> expectations =
          createPreventionExpectations(
              List.of(savedAgent1), savedEndpoint, savedAssetGroup, EXPIRATION_TIME_1_s);
      expectations.addAll(
          createDetectionExpectations(
              List.of(savedAgent1), savedEndpoint, savedAssetGroup, EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

      em.flush();
      em.clear();

      // The security platform answers the agent green on both prevention and detection
      List<BaseInjectExpectation> agentExpectations =
          injectExpectationRepository.findAllByInjectAndAgent(
              savedInject.getId(), savedAgent1.getId());
      assertEquals(2, agentExpectations.size());
      agentExpectations.forEach(
          expectation -> {
            expectation.setResults(
                List.of(
                    InjectExpectationResult.builder()
                        .sourceId("microsoft-defender")
                        .sourceName("Microsoft Defender")
                        .sourceType("security-platform")
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .result("Prevented")
                        .sourceAssetId(UUID.randomUUID().toString())
                        .score(100.0)
                        .build()));
            expectation.setScore(100.0);
          });
      injectExpectationRepository.saveAll(agentExpectations);

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT -- full green on agent, asset and asset group for both types
      injectExpectationRepository
          .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
          .forEach(
              expectation -> {
                assertEquals(100.0, expectation.getScore());
                assertEquals(
                    BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, expectation.getResponse());
              });
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
    @DisplayName("Parent expectations expiring before their agents stay pending, never failed")
    void parentExpirationBeforeAgentsStaysPending() {
      // Regression companion: parent asset / asset group expectations reaching their expiration
      // while their agent children are still legitimately pending (agents have a longer
      // expiration window) must stay pending - the verdict belongs to the children.
      long agentExpirationSeconds = 3600L;
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              agentExpirationSeconds);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

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

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT -- everything still pending: agents within their window, parents wait for them
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
    @DisplayName("Asset expectations without agent expectation linked")
    void assetExpectationWithoutAgentExpectationsLinked() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, detectionExpectations);

      em.flush();
      em.clear();

      // Delete agent inject expectations to test behavior of assets without agents
      List<BaseInjectExpectation> injectExpectations =
          List.of(
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent1.getId())
                  .getFirst(),
              injectExpectationRepository
                  .findAllByInjectAndAgent(savedInject.getId(), savedAgent2.getId())
                  .getFirst());

      List<String> ids = injectExpectations.stream().map(BaseInjectExpectation::getId).toList();

      injectExpectationRepository.deleteAllById(ids);

      // -- VERIFY --
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
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, null, EXPIRATION_TIME_1_s, null);

      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, List.of(expectation));

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
      assertEquals(100.0, injectExpectations.getFirst().getScore());
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
      List<Expectation> expectations = new ArrayList<>();
      expectations.add(
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, null, EXPIRATION_TIME_1_s, null));
      expectations.add(
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              null,
              EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

      em.flush();
      em.clear();

      // The assessment injector answers the ASSET row directly: proven vulnerable
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      BaseInjectExpectation assetExpectation = assetExpectations.getFirst();
      assetExpectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId("nuclei-security-platform")
                  .sourceName("Nuclei")
                  .sourceType("security-platform")
                  .sourcePlatform(
                      SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
                  .result("Vulnerable")
                  .sourceAssetId(UUID.randomUUID().toString())
                  .score(0.0)
                  .build()));
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
      assertEquals(100.0, agentExpectations.getFirst().getScore());
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
      List<Expectation> expectations = new ArrayList<>();
      expectations.add(
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, savedAssetGroup, EXPIRATION_TIME_1_s, null));
      expectations.add(
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s));
      expectations.add(
          vulnerabilityExpectationForAssetGroup(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedAssetGroup,
              false,
              EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

      em.flush();
      em.clear();

      // The assessment injector answers the ASSET and ASSET GROUP rows directly: proven vulnerable
      InjectExpectationResult nucleiVerdict =
          InjectExpectationResult.builder()
              .sourceId("nuclei-security-platform")
              .sourceName("Nuclei")
              .sourceType("security-platform")
              .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
              .result("Vulnerable")
              .sourceAssetId(UUID.randomUUID().toString())
              .score(0.0)
              .build();
      BaseInjectExpectation assetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      assetExpectation.setResults(new ArrayList<>(List.of(nucleiVerdict)));
      assetExpectation.setScore(0.0);
      BaseInjectExpectation assetGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assetGroupExpectation.setResults(new ArrayList<>(List.of(nucleiVerdict)));
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
      assertEquals(100.0, agentExpectations.getFirst().getScore());
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
      List<Expectation> expectations = new ArrayList<>();
      expectations.add(
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, savedAssetGroup, EXPIRATION_TIME_1_s, null));
      expectations.add(
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s));
      expectations.add(
          vulnerabilityExpectationForAssetGroup(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedAssetGroup,
              false,
              EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

      em.flush();
      em.clear();

      // The scanner answers the ASSET and ASSET GROUP rows directly: nothing found
      InjectExpectationResult nucleiVerdict =
          InjectExpectationResult.builder()
              .sourceId("nuclei-security-platform")
              .sourceName("Nuclei")
              .sourceType("security-platform")
              .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER.name())
              .result("Not vulnerable")
              .sourceAssetId(UUID.randomUUID().toString())
              .score(100.0)
              .build();
      BaseInjectExpectation assetExpectation =
          injectExpectationRepository
              .findAllByInjectAndAsset(savedInject.getId(), savedEndpoint.getId())
              .getFirst();
      assetExpectation.setResults(new ArrayList<>(List.of(nucleiVerdict)));
      assetExpectation.setScore(100.0);
      BaseInjectExpectation assetGroupExpectation =
          injectExpectationRepository
              .findAllByInjectAndAssetGroup(savedInject.getId(), savedAssetGroup.getId())
              .getFirst();
      assetGroupExpectation.setResults(new ArrayList<>(List.of(nucleiVerdict)));
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
      assertEquals(100.0, agentExpectations.getFirst().getScore());
      assertTrue(
          agentExpectations.getFirst().getResults().stream()
              .anyMatch(result -> COLLECTOR_ID.equals(result.getSourceId())));
      // Asset: verdict unchanged, and NO redundant manager row next to the Nuclei answer
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, assetExpectations.getFirst().getScore());
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
      assertEquals(100.0, assetGroupExpectations.getFirst().getScore());
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
      List<Expectation> expectations = new ArrayList<>();
      expectations.add(
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, null, EXPIRATION_TIME_1_s, null));
      expectations.add(
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              null,
              EXPIRATION_TIME_1_s));
      injectExpectationService.buildAndSaveInjectExpectations(executableInject, expectations);

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
      InjectExpectationUpdateInput input = new InjectExpectationUpdateInput();
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
      assertEquals(100.0, assetExpectations.getFirst().getScore());
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
    @DisplayName("An agentless vulnerability leaf expires to Not vulnerable, not to failed")
    void agentlessVulnerabilityLeafExpiresToNotVulnerable() {
      // For VULNERABILITY, silence means "nothing found": an agentless asset expectation that no
      // scanner ever answered must expire to the success default (Not vulnerable), matching the
      // agent-level expiration behavior - not to a failed "Vulnerable" verdict.
      ExecutableInject executableInject = newExecutableInjectWithTargets();
      Expectation expectation =
          vulnerabilityExpectationForAsset(
              100.0,
              "Vulnerability",
              "Vulnerability Expectation",
              savedEndpoint,
              null,
              EXPIRATION_TIME_1_s);
      injectExpectationService.buildAndSaveInjectExpectations(
          executableInject, List.of(expectation));

      em.flush();
      em.clear();

      // -- EXECUTE --
      expireExpectationsInDbByInjectId(savedInject.getId());
      expectationsExpirationManagerService.computeExpectations(savedInject.getTenant().getId());

      // -- ASSERT --
      List<BaseInjectExpectation> assetExpectations =
          injectExpectationRepository.findAllByInjectAndAsset(
              savedInject.getId(), savedEndpoint.getId());
      assertEquals(100.0, assetExpectations.getFirst().getScore());
      assertEquals(
          BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
          assetExpectations.getFirst().getResponse());
    }
  }

  // -- PRIVATE HELPERS --

  private List<LogEvent> captureExpectationResultEvents(int expectedMinimumEvents) {
    ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
    verify(auditLogTransportDispatcherUtils, timeout(5000).atLeastOnce())
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
        .setParameter("past", Instant.now().minus(1, ChronoUnit.HOURS))
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
        emptyList());
  }
}
