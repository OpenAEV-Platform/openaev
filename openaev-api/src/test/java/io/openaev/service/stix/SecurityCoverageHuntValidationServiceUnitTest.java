package io.openaev.service.stix;

import static io.openaev.service.stix.SecurityCoverageHuntValidationService.MAX_ERROR_LENGTH;
import static io.openaev.service.stix.SecurityCoverageHuntValidationService.MIN_WINDOW_LENGTH;
import static io.openaev.service.stix.SecurityCoverageHuntValidationService.RETRY_BASE_DELAY;
import static io.openaev.service.stix.SecurityCoverageHuntValidationService.RETRY_MAX_DELAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.SecurityCoverageHuntValidation;
import io.openaev.database.model.SecurityCoverageHuntValidation.Status;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SecurityCoverageHuntValidationRepository;
import io.openaev.opencti.client.mutations.ValidateHuntFromEmulation;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.opencti.errors.ConnectorUnavailableError;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.AssetService;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.ExecutionWindow;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationRequest;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.hc.client5.http.ClientProtocolException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityCoverageHuntValidationService (unit)")
class SecurityCoverageHuntValidationServiceUnitTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String SIMULATION_ID = "simulation-1";
  private static final String COVERAGE_EXTERNAL_ID =
      "security-coverage--2c5b3a1e-9f0d-4d6b-8a1c-3e4f5a6b7c8d";
  private static final Instant SENT = Instant.parse("2026-10-03T10:00:00Z");
  private static final Instant ENDED = Instant.parse("2026-10-03T10:12:00Z");
  private static final Duration PADDING = Duration.ofMinutes(5);

  @Spy
  private SecurityCoverageHuntValidationConfig config = new SecurityCoverageHuntValidationConfig();

  @Mock private SecurityCoverageHuntValidationRepository huntValidationRepository;
  @Mock private ExerciseService exerciseService;
  @Mock private AssetService assetService;
  @Mock private OpenCTIConnectorService openCTIConnectorService;
  @Mock private ResultsMetricCollector resultsMetricCollector;

  @InjectMocks private SecurityCoverageHuntValidationService service;

  /** Three minutes before the claims of {@link #request(String)} end. */
  private final SteppingClock clock = new SteppingClock(SENT.minus(Duration.ofMinutes(3)));

  @BeforeEach
  void useSteppingClock() {
    service.clock = clock;
  }

  @AfterEach
  void leaveTransaction() {
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  /** A clock that only moves when a test advances it. */
  private static final class SteppingClock extends Clock {
    private Instant now;

    SteppingClock(Instant start) {
      this.now = start;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static void insideTransaction() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  // -- builders --

  private static SecurityPlatform platform(
      String id, SecurityPlatform.SECURITY_PLATFORM_TYPE type) {
    SecurityPlatform platform = new SecurityPlatform();
    platform.setId(id);
    platform.setName("Platform " + id);
    platform.setSecurityPlatformType(type);
    return platform;
  }

  private static AttackPattern technique(String externalId) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setExternalId(externalId);
    return attackPattern;
  }

  private static InjectStatus status(ExecutionStatus name, Instant sent, Instant ended) {
    InjectStatus status = new InjectStatus();
    status.setName(name);
    status.setTrackingSentDate(sent);
    status.setTrackingEndDate(ended);
    return status;
  }

  private static InjectExpectationResult result(String sourceAssetId, Double score) {
    return InjectExpectationResult.builder()
        .sourceId("collector-" + sourceAssetId)
        .sourceAssetId(sourceAssetId)
        .score(score)
        .result(score == null ? null : "Detected")
        .build();
  }

  private static BaseInjectExpectation expectation(
      BaseInjectExpectation expectation, InjectExpectationResult... results) {
    expectation.setResults(new ArrayList<>(List.of(results)));
    return expectation;
  }

  private static Inject inject(
      String id,
      InjectStatus status,
      List<AttackPattern> techniques,
      BaseInjectExpectation... exps) {
    InjectorContract contract = new InjectorContract();
    contract.setAttackPatterns(new ArrayList<>(techniques));
    Inject inject = new Inject();
    inject.setId(id);
    inject.setInjectorContract(contract);
    if (status != null) {
      status.setInject(inject);
      inject.setStatus(status);
    }
    inject.setExpectations(new ArrayList<>(List.of(exps)));
    return inject;
  }

  private static Exercise simulation(SecurityCoverage coverage, Inject... injects) {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);
    Exercise simulation = new Exercise();
    simulation.setId(SIMULATION_ID);
    simulation.setTenant(tenant);
    simulation.setSecurityCoverage(coverage);
    simulation.setInjects(new ArrayList<>(List.of(injects)));
    return simulation;
  }

  private static SecurityCoverage coverage() {
    SecurityCoverage coverage = new SecurityCoverage();
    coverage.setExternalId(COVERAGE_EXTERNAL_ID);
    return coverage;
  }

  /** A request claimed until {@code SENT}, stale long after it. */
  private static HuntValidationRequest request(String id) {
    return request(id, SENT, SENT.plus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE));
  }

  private static HuntValidationRequest request(String id, Instant leaseUntil, Instant expiresAt) {
    return new HuntValidationRequest(
        id,
        "inject-" + id,
        "T1059.001",
        "platform-" + id,
        "Splunk prod",
        COVERAGE_EXTERNAL_ID,
        SENT.minus(PADDING),
        ENDED.plus(PADDING),
        leaseUntil,
        expiresAt);
  }

  private static SecurityCoverageHuntValidation pendingValidation(String id, int attempts) {
    SecurityCoverageHuntValidation validation = new SecurityCoverageHuntValidation();
    validation.setId(id);
    validation.setStatus(Status.PENDING);
    validation.setAttempts(attempts);
    validation.setNextAttemptAt(SENT);
    return validation;
  }

  @Nested
  @DisplayName("Planning rules")
  class PlanningRules {

    @Test
    @DisplayName("given an executed inject should pad its sent-to-end window on both sides")
    void given_executedInject_should_padWindow() {
      // Arrange
      Inject inject = inject("i", status(ExecutionStatus.EXECUTED, SENT, ENDED), List.of());

      // Act
      Optional<ExecutionWindow> window =
          SecurityCoverageHuntValidationService.executionWindow(inject, PADDING);

      // Assert
      assertThat(window).contains(new ExecutionWindow(SENT.minus(PADDING), ENDED.plus(PADDING)));
    }

    @Test
    @DisplayName("given a partial inject without end date should end the window at its start")
    void given_partialInjectWithoutEnd_should_endAtStart() {
      // Arrange
      Inject inject = inject("i", status(ExecutionStatus.PARTIAL, SENT, null), List.of());

      // Act
      Optional<ExecutionWindow> window =
          SecurityCoverageHuntValidationService.executionWindow(inject, PADDING);

      // Assert
      assertThat(window).contains(new ExecutionWindow(SENT.minus(PADDING), SENT.plus(PADDING)));
    }

    @Test
    @DisplayName("given an end date before the start should never produce an inverted window")
    void given_endBeforeStart_should_notInvertWindow() {
      // Arrange
      Inject inject =
          inject("i", status(ExecutionStatus.EXECUTED, SENT, SENT.minusSeconds(30)), List.of());

      // Act
      Optional<ExecutionWindow> window =
          SecurityCoverageHuntValidationService.executionWindow(inject, PADDING);

      // Assert
      assertThat(window).contains(new ExecutionWindow(SENT.minus(PADDING), SENT.plus(PADDING)));
    }

    @Test
    @DisplayName("given no padding and an instant execution should keep the minimum window")
    void given_noPaddingAndInstantExecution_should_keepMinimumWindow() {
      // Arrange
      Inject withoutEnd = inject("i", status(ExecutionStatus.PARTIAL, SENT, null), List.of());
      Inject endedAtOnce = inject("j", status(ExecutionStatus.EXECUTED, SENT, SENT), List.of());

      // Act
      Optional<ExecutionWindow> windowWithoutEnd =
          SecurityCoverageHuntValidationService.executionWindow(withoutEnd, Duration.ZERO);
      Optional<ExecutionWindow> windowEndedAtOnce =
          SecurityCoverageHuntValidationService.executionWindow(endedAtOnce, Duration.ZERO);

      // Assert
      ExecutionWindow minimum = new ExecutionWindow(SENT, SENT.plus(MIN_WINDOW_LENGTH));
      assertThat(windowWithoutEnd).contains(minimum);
      assertThat(windowEndedAtOnce).contains(minimum);
    }

    @Test
    @DisplayName("given an inject that did not run should have no window")
    void given_injectThatDidNotRun_should_haveNoWindow() {
      // Arrange + Act + Assert
      for (ExecutionStatus name :
          List.of(
              ExecutionStatus.ERROR,
              ExecutionStatus.QUEUING,
              ExecutionStatus.EXECUTING,
              ExecutionStatus.PENDING,
              ExecutionStatus.DRAFT)) {
        Inject inject = inject("i", status(name, SENT, ENDED), List.of());
        assertThat(SecurityCoverageHuntValidationService.executionWindow(inject, PADDING))
            .as("status %s", name)
            .isEmpty();
      }
      assertThat(
              SecurityCoverageHuntValidationService.executionWindow(
                  inject("i", null, List.of()), PADDING))
          .isEmpty();
      assertThat(
              SecurityCoverageHuntValidationService.executionWindow(
                  inject("i", status(ExecutionStatus.EXECUTED, null, ENDED), List.of()), PADDING))
          .isEmpty();
    }

    @Test
    @DisplayName("given techniques with blanks and duplicates should keep sorted distinct ids")
    void given_techniques_should_keepSortedDistinctIds() {
      // Arrange
      Inject inject =
          inject(
              "i",
              null,
              List.of(
                  technique("T1059.001"),
                  technique(" T1003 "),
                  technique(""),
                  technique(null),
                  technique("T1059.001")));

      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.techniqueIds(inject))
          .containsExactly("T1003", "T1059.001");
    }

    @Test
    @DisplayName("given every verdict of a platform computed should consider it ready")
    void given_everyVerdictComputed_should_beReady() {
      // Arrange
      Inject inject =
          inject(
              "i",
              null,
              List.of(),
              expectation(new DetectionInjectExpectation(), result("edr", 100.0)),
              expectation(new PreventionInjectExpectation(), result("edr", 0.0)));

      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.readySecurityPlatformIds(inject))
          .containsExactly("edr");
    }

    @Test
    @DisplayName("given one verdict still pending should not consider the platform ready")
    void given_oneVerdictPending_should_notBeReady() {
      // Arrange
      Inject inject =
          inject(
              "i",
              null,
              List.of(),
              expectation(
                  new DetectionInjectExpectation(), result("edr", 100.0), result("siem", 100.0)),
              expectation(new PreventionInjectExpectation(), result("edr", null)));

      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.readySecurityPlatformIds(inject))
          .containsExactly("siem");
    }

    @Test
    @DisplayName(
        "given results outside detection and prevention or without asset should ignore them")
    void given_unrelatedResults_should_beIgnored() {
      // Arrange
      Inject inject =
          inject(
              "i",
              null,
              List.of(),
              expectation(new ManualInjectExpectation(), result("manual", 100.0)),
              expectation(new DetectionInjectExpectation(), result(null, 100.0)),
              expectation(new DetectionInjectExpectation(), result(" ", 100.0)));

      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.readySecurityPlatformIds(inject)).isEmpty();
    }

    @Test
    @DisplayName("given consecutive failures should back off exponentially up to the cap")
    void given_consecutiveFailures_should_backOffUpToCap() {
      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.retryDelay(1)).isEqualTo(RETRY_BASE_DELAY);
      assertThat(SecurityCoverageHuntValidationService.retryDelay(2))
          .isEqualTo(RETRY_BASE_DELAY.multipliedBy(2));
      assertThat(SecurityCoverageHuntValidationService.retryDelay(4))
          .isEqualTo(RETRY_BASE_DELAY.multipliedBy(8));
      assertThat(SecurityCoverageHuntValidationService.retryDelay(30)).isEqualTo(RETRY_MAX_DELAY);
      assertThat(SecurityCoverageHuntValidationService.retryDelay(0)).isEqualTo(RETRY_BASE_DELAY);
    }

    @Test
    @DisplayName("given a transport error should describe it with its cause on one line")
    void given_transportError_should_describeWithCause() {
      // Arrange
      IOException error =
          new ClientProtocolException(
              "Unexpected response for request on: http://opencti/graphql",
              new ConnectException("Connection\nrefused"));

      // Act + Assert
      assertThat(SecurityCoverageHuntValidationService.describe(error))
          .isEqualTo(
              "Unexpected response for request on: http://opencti/graphql: Connection refused");
    }
  }

  @Nested
  @DisplayName("Planning a simulation")
  class PlanningASimulation {

    private final SecurityPlatform edr =
        platform("edr", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);

    @BeforeEach
    void setUp() {
      insideTransaction();
    }

    private Inject executedInjectSeenBy(String injectId, String platformId, String... techniques) {
      return inject(
          injectId,
          status(ExecutionStatus.EXECUTED, SENT, ENDED),
          Arrays.stream(techniques)
              .map(SecurityCoverageHuntValidationServiceUnitTest::technique)
              .toList(),
          expectation(new DetectionInjectExpectation(), result(platformId, 100.0)));
    }

    @SuppressWarnings("unchecked")
    private List<SecurityCoverageHuntValidation> savedValidations() {
      ArgumentCaptor<List<SecurityCoverageHuntValidation>> saved =
          ArgumentCaptor.forClass((Class) List.class);
      verify(huntValidationRepository).saveAll(saved.capture());
      return saved.getValue();
    }

    @Test
    @DisplayName("given a ready inject should plan one validation per technique with its payload")
    void given_readyInject_should_planOneValidationPerTechnique() {
      // Arrange
      Exercise simulation =
          simulation(coverage(), executedInjectSeenBy("inject-1", "edr", "T1059.001", "T1003"));
      when(exerciseService.exercise(SIMULATION_ID)).thenReturn(simulation);
      when(assetService.securityPlatformsByIds(anySet())).thenReturn(List.of(edr));
      when(huntValidationRepository.findAllByInjectIdIn(anyCollection())).thenReturn(List.of());

      // Act
      int planned = service.planForSimulation(SIMULATION_ID);

      // Assert
      assertThat(planned).isEqualTo(2);
      List<SecurityCoverageHuntValidation> saved = savedValidations();
      assertThat(saved)
          .extracting(SecurityCoverageHuntValidation::getTechniqueId)
          .containsExactly("T1003", "T1059.001");
      SecurityCoverageHuntValidation first = saved.getFirst();
      assertThat(first.getInjectId()).isEqualTo("inject-1");
      assertThat(first.getSecurityPlatformId()).isEqualTo("edr");
      assertThat(first.getSecurityPlatformName()).isEqualTo("Platform edr");
      assertThat(first.getCoverageExternalId()).isEqualTo(COVERAGE_EXTERNAL_ID);
      assertThat(first.getWindowStart()).isEqualTo(SENT.minus(PADDING));
      assertThat(first.getWindowEnd()).isEqualTo(ENDED.plus(PADDING));
      assertThat(first.getStatus()).isEqualTo(Status.PENDING);
      assertThat(first.getAttempts()).isZero();
      assertThat(first.getNextAttemptAt()).isNotNull();
      assertThat(first.getTenant().getId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("given triples already planned should only plan the new ones")
    void given_alreadyPlanned_should_onlyPlanNewOnes() {
      // Arrange
      Exercise simulation =
          simulation(coverage(), executedInjectSeenBy("inject-1", "edr", "T1059.001", "T1003"));
      when(exerciseService.exercise(SIMULATION_ID)).thenReturn(simulation);
      when(assetService.securityPlatformsByIds(anySet())).thenReturn(List.of(edr));
      SecurityCoverageHuntValidation existing = new SecurityCoverageHuntValidation();
      existing.setInjectId("inject-1");
      existing.setTechniqueId("T1059.001");
      existing.setSecurityPlatformId("edr");
      when(huntValidationRepository.findAllByInjectIdIn(List.of("inject-1")))
          .thenReturn(List.of(existing));

      // Act
      int planned = service.planForSimulation(SIMULATION_ID);

      // Assert
      assertThat(planned).isEqualTo(1);
      assertThat(savedValidations())
          .extracting(SecurityCoverageHuntValidation::getTechniqueId)
          .containsExactly("T1003");
    }

    @Test
    @DisplayName("given a platform type OpenCTI hunts cannot run on should plan nothing")
    void given_nonHuntablePlatform_should_planNothing() {
      // Arrange
      Exercise simulation =
          simulation(coverage(), executedInjectSeenBy("inject-1", "mail", "T1566"));
      when(exerciseService.exercise(SIMULATION_ID)).thenReturn(simulation);
      when(assetService.securityPlatformsByIds(anySet()))
          .thenReturn(
              List.of(platform("mail", SecurityPlatform.SECURITY_PLATFORM_TYPE.EMAIL_SECURITY)));

      // Act
      int planned = service.planForSimulation(SIMULATION_ID);

      // Assert
      assertThat(planned).isZero();
      verify(huntValidationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("given a simulation without coverage should plan nothing and read nothing else")
    void given_noCoverage_should_planNothing() {
      // Arrange
      when(exerciseService.exercise(SIMULATION_ID))
          .thenReturn(simulation(null, executedInjectSeenBy("inject-1", "edr", "T1003")));

      // Act
      int planned = service.planForSimulation(SIMULATION_ID);

      // Assert
      assertThat(planned).isZero();
      verify(assetService, never()).securityPlatformsByIds(anySet());
      verify(huntValidationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("given no inject ready yet should not even resolve the platforms")
    void given_noInjectReady_should_notResolvePlatforms() {
      // Arrange
      Inject pending =
          inject(
              "inject-1",
              status(ExecutionStatus.EXECUTED, SENT, ENDED),
              List.of(technique("T1003")),
              expectation(new DetectionInjectExpectation(), result("edr", null)));
      when(exerciseService.exercise(SIMULATION_ID)).thenReturn(simulation(coverage(), pending));

      // Act
      int planned = service.planForSimulation(SIMULATION_ID);

      // Assert
      assertThat(planned).isZero();
      verify(assetService, never()).securityPlatformsByIds(anySet());
    }

    @Test
    @DisplayName("given no transaction should refuse instead of reading nothing")
    void given_noTransaction_should_refuse() {
      // Arrange
      TransactionSynchronizationManager.setActualTransactionActive(false);

      // Act + Assert
      assertThatThrownBy(() -> service.planForSimulation(SIMULATION_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("tenant-scoped transaction");
      verify(exerciseService, never()).exercise(any());
    }
  }

  @Nested
  @DisplayName("Sending")
  class Sending {

    private ValidateHuntFromEmulation.HuntValidation huntValidation(int hunts, int runs) {
      ValidateHuntFromEmulation.HuntValidation validation =
          new ValidateHuntFromEmulation.HuntValidation();
      validation.setHuntsCount(hunts);
      List<ValidateHuntFromEmulation.HuntRun> huntRuns = new ArrayList<>();
      for (int i = 0; i < runs; i++) {
        huntRuns.add(new ValidateHuntFromEmulation.HuntRun());
      }
      validation.setRuns(huntRuns);
      return validation;
    }

    @Test
    @DisplayName(
        "given a due validation should send the contract input with the bundle platform id")
    void given_dueValidation_should_sendContractInput() throws Exception {
      // Arrange
      Duration requestTimeout = config.getRequestTimeout();
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenReturn(huntValidation(2, 3));

      // Act
      List<HuntValidationOutcome> outcomes = service.send(TENANT_ID, List.of(request("1")));

      // Assert
      ArgumentCaptor<ValidateHuntFromEmulation.Input> input =
          ArgumentCaptor.forClass(ValidateHuntFromEmulation.Input.class);
      verify(openCTIConnectorService)
          .validateHuntFromEmulation(eq(TENANT_ID), input.capture(), eq(requestTimeout));
      assertThat(input.getValue())
          .isEqualTo(
              new ValidateHuntFromEmulation.Input(
                  "T1059.001",
                  SecurityPlatform.stixIdentityId("Splunk prod"),
                  "Splunk prod",
                  "inject-1",
                  "2026-10-03T09:55:00Z",
                  "2026-10-03T10:17:00Z",
                  COVERAGE_EXTERNAL_ID));
      assertThat(outcomes)
          .containsExactly(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.VALIDATED, 2, 3, null, SENT));
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(1L);
    }

    @Test
    @DisplayName("given OpenCTI refusing one validation should keep sending the others")
    void given_refusal_should_keepSendingOthers() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenThrow(new ConnectorError("Enterprise edition is not enabled"))
          .thenThrow(new IllegalArgumentException("unexpected payload"))
          .thenReturn(huntValidation(0, 0));

      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(TENANT_ID, List.of(request("1"), request("2"), request("3")));

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::kind)
          .containsExactly(
              HuntValidationOutcome.Kind.REFUSED,
              HuntValidationOutcome.Kind.REFUSED,
              HuntValidationOutcome.Kind.VALIDATED);
      assertThat(outcomes.getFirst().error()).isEqualTo("Enterprise edition is not enabled");
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(1L);
    }

    @Test
    @DisplayName("given OpenCTI unreachable should stop calling and postpone the rest of the batch")
    void given_unreachable_should_stopTheBatch() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenReturn(huntValidation(1, 1))
          .thenThrow(new ClientProtocolException("Connection refused"));

      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(TENANT_ID, List.of(request("1"), request("2"), request("3")));

      // Assert
      assertThat(outcomes)
          .containsExactly(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.VALIDATED, 1, 1, null, SENT),
              new HuntValidationOutcome(
                  "2",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Connection refused",
                  SENT),
              new HuntValidationOutcome(
                  "3",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Connection refused",
                  SENT));
      verify(openCTIConnectorService, times(2)).validateHuntFromEmulation(any(), any(), any());
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(1L);
    }

    @Test
    @DisplayName("given OpenCTI unreachable from the first call should send nothing")
    void given_unreachableFromFirstCall_should_sendNothing() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenThrow(new ClientProtocolException("Connection refused"));

      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(TENANT_ID, List.of(request("1"), request("2")));

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::kind)
          .containsOnly(HuntValidationOutcome.Kind.UNREACHABLE)
          .hasSize(2);
      verify(openCTIConnectorService, times(1)).validateHuntFromEmulation(any(), any(), any());
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(0L);
    }

    @Test
    @DisplayName(
        "given the tenant connector unavailable should postpone like an outage, not refuse")
    void given_connectorUnavailable_should_postponeLikeAnOutage() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenThrow(new ConnectorUnavailableError("connector hasn't registered yet"));

      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(TENANT_ID, List.of(request("1"), request("2")));

      // Assert
      assertThat(outcomes)
          .containsExactly(
              new HuntValidationOutcome(
                  "1",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "connector hasn't registered yet",
                  SENT),
              new HuntValidationOutcome(
                  "2",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "connector hasn't registered yet",
                  SENT));
      verify(openCTIConnectorService, times(1)).validateHuntFromEmulation(any(), any(), any());
    }

    @Test
    @DisplayName("given the tenant budget already spent should start no call and defer all")
    void given_budgetSpent_should_startNoCall() throws Exception {
      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(TENANT_ID, List.of(request("1"), request("2")), clock.instant());

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::validationId, HuntValidationOutcome::kind)
          .containsExactly(
              tuple("1", HuntValidationOutcome.Kind.DEFERRED),
              tuple("2", HuntValidationOutcome.Kind.DEFERRED));
      verify(openCTIConnectorService, never()).validateHuntFromEmulation(any(), any(), any());
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(0L);
    }

    @Test
    @DisplayName("given the tenant budget spent during a call should defer the rest untried")
    void given_budgetSpentDuringCall_should_deferTheRestUntried() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenAnswer(
              invocation -> {
                clock.advance(Duration.ofMillis(600));
                return huntValidation(1, 1);
              });

      // Act
      List<HuntValidationOutcome> outcomes =
          service.send(
              TENANT_ID,
              List.of(request("1"), request("2"), request("3")),
              clock.instant().plusMillis(500));

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::validationId, HuntValidationOutcome::kind)
          .containsExactly(
              tuple("1", HuntValidationOutcome.Kind.VALIDATED),
              tuple("2", HuntValidationOutcome.Kind.DEFERRED),
              tuple("3", HuntValidationOutcome.Kind.DEFERRED));
      assertThat(outcomes).extracting(HuntValidationOutcome::leaseUntil).containsOnly(SENT);
      verify(openCTIConnectorService, times(1)).validateHuntFromEmulation(any(), any(), any());
      verify(resultsMetricCollector).recordCoverageHuntValidationsSent(1L);
    }

    @Test
    @DisplayName("given a request turned stale while waiting should give it up without a call")
    void given_requestTurnedStale_should_giveItUpWithoutACall() throws Exception {
      // Arrange
      when(openCTIConnectorService.validateHuntFromEmulation(eq(TENANT_ID), any(), any()))
          .thenReturn(huntValidation(1, 1));
      HuntValidationRequest stale = request("1", SENT, clock.instant());

      // Act
      List<HuntValidationOutcome> outcomes = service.send(TENANT_ID, List.of(stale, request("2")));

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::validationId, HuntValidationOutcome::kind)
          .containsExactly(
              tuple("1", HuntValidationOutcome.Kind.EXPIRED),
              tuple("2", HuntValidationOutcome.Kind.VALIDATED));
      assertThat(outcomes.getFirst().error())
          .isEqualTo(
              "Not delivered within " + SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE);
      ArgumentCaptor<ValidateHuntFromEmulation.Input> input =
          ArgumentCaptor.forClass(ValidateHuntFromEmulation.Input.class);
      verify(openCTIConnectorService, times(1))
          .validateHuntFromEmulation(eq(TENANT_ID), input.capture(), any());
      assertThat(input.getValue().injectId()).isEqualTo("inject-2");
    }

    @Test
    @DisplayName("given a call that could outlive its claim should defer it without a call")
    void given_callOutlivingItsClaim_should_deferItWithoutACall() throws Exception {
      // Arrange
      Instant claimEnd = clock.instant().plus(config.getRequestTimeout());
      HuntValidationRequest closing =
          request("1", claimEnd, SENT.plus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE));

      // Act
      List<HuntValidationOutcome> outcomes = service.send(TENANT_ID, List.of(closing));

      // Assert
      assertThat(outcomes)
          .extracting(HuntValidationOutcome::kind, HuntValidationOutcome::leaseUntil)
          .containsExactly(tuple(HuntValidationOutcome.Kind.DEFERRED, claimEnd));
      verify(openCTIConnectorService, never()).validateHuntFromEmulation(any(), any(), any());
    }

    @Test
    @DisplayName("given an open transaction should refuse to hold it across the HTTP calls")
    void given_openTransaction_should_refuse() throws Exception {
      // Arrange
      insideTransaction();

      // Act + Assert
      assertThatThrownBy(() -> service.send(TENANT_ID, List.of(request("1"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("must not run inside a transaction");
      verify(openCTIConnectorService, never()).validateHuntFromEmulation(any(), any(), any());
      verify(resultsMetricCollector, never()).recordCoverageHuntValidationsSent(anyLong());
    }
  }

  @Nested
  @DisplayName("Collecting due validations")
  class CollectingDueValidations {

    private static final Instant NOW = Instant.parse("2026-10-03T11:00:00Z");

    @BeforeEach
    void setUp() {
      insideTransaction();
    }

    private static final Instant STALE_BEFORE =
        NOW.minus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE);

    private void givenDue(SecurityCoverageHuntValidation... validations) {
      when(huntValidationRepository.findDueForUpdateSkipLocked(
              eq(Status.PENDING), eq(NOW), eq(STALE_BEFORE), any()))
          .thenReturn(List.of(validations));
    }

    private void givenStale(SecurityCoverageHuntValidation... validations) {
      when(huntValidationRepository.findStaleForUpdateSkipLocked(
              eq(Status.PENDING), eq(NOW), eq(STALE_BEFORE), any()))
          .thenReturn(List.of(validations));
    }

    @Test
    @DisplayName("given fresh due validations should claim them until the end of the lease")
    void given_freshDueValidations_should_claimThem() {
      // Arrange
      SecurityCoverageHuntValidation fresh = pendingValidation("1", 0);
      fresh.setCreatedAt(NOW.minus(Duration.ofHours(1)));
      givenDue(fresh);
      Instant leaseUntil = NOW.plus(service.claimLease());

      // Act
      List<HuntValidationRequest> requests = service.collectDueRequests(NOW);

      // Assert
      assertThat(requests)
          .extracting(HuntValidationRequest::id, HuntValidationRequest::leaseUntil)
          .containsExactly(tuple("1", leaseUntil));
      assertThat(fresh.getNextAttemptAt()).isEqualTo(leaseUntil);
      assertThat(fresh.getAttempts()).isZero();
      verify(huntValidationRepository).saveAll(List.of(fresh));
    }

    @Test
    @DisplayName("given nothing due should claim and write nothing")
    void given_nothingDue_should_writeNothing() {
      // Arrange
      givenDue();

      // Act
      List<HuntValidationRequest> requests = service.collectDueRequests(NOW);

      // Assert
      assertThat(requests).isEmpty();
      verify(huntValidationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("given a lease covering the send budget and one request should outlive a delivery")
    void given_requestTimeout_should_extendTheLease() {
      // Arrange
      config.setRequestTimeout(Duration.ofMinutes(3));

      // Act
      Duration lease = service.claimLease();

      // Assert
      assertThat(lease)
          .isEqualTo(
              SecurityCoverageHuntValidationService.TENANT_SEND_BUDGET
                  .plus(Duration.ofMinutes(3))
                  .plus(SecurityCoverageHuntValidationService.CLAIM_LEASE_MARGIN));
    }

    @Test
    @DisplayName(
        "given stale due validations should give them up apart from the fresh ones they never delay")
    void given_expiredDueValidation_should_giveUpBeforeSending() {
      // Arrange
      SecurityCoverageHuntValidation expired = pendingValidation("1", 2);
      expired.setCreatedAt(STALE_BEFORE);
      expired.setLastError("Connection refused");
      SecurityCoverageHuntValidation fresh = pendingValidation("2", 0);
      fresh.setCreatedAt(NOW.minus(Duration.ofHours(1)));
      givenStale(expired);
      givenDue(fresh);

      // Act
      List<HuntValidationRequest> requests = service.collectDueRequests(NOW);

      // Assert
      assertThat(requests)
          .extracting(HuntValidationRequest::id, HuntValidationRequest::expiresAt)
          .containsExactly(
              tuple(
                  "2",
                  fresh.getCreatedAt().plus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE)));
      verify(huntValidationRepository)
          .findStaleForUpdateSkipLocked(
              Status.PENDING,
              NOW,
              STALE_BEFORE,
              PageRequest.of(0, SecurityCoverageHuntValidationService.STALE_CHUNK_SIZE));
      verify(huntValidationRepository)
          .findDueForUpdateSkipLocked(
              Status.PENDING, NOW, STALE_BEFORE, PageRequest.of(0, config.getBatchSize()));
      assertThat(expired.getStatus()).isEqualTo(Status.FAILED);
      assertThat(expired.getAttempts()).isEqualTo(2);
      assertThat(expired.getLastError())
          .startsWith(
              "Not delivered within " + SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE)
          .endsWith("last error: Connection refused");
      assertThat(fresh.getStatus()).isEqualTo(Status.PENDING);
      assertThat(fresh.getNextAttemptAt()).isEqualTo(NOW.plus(service.claimLease()));
      verify(huntValidationRepository).saveAll(List.of(expired, fresh));
    }

    @Test
    @DisplayName("given no transaction should refuse to read the tenant-active table")
    void given_noTransaction_should_refuse() {
      // Arrange
      TransactionSynchronizationManager.setActualTransactionActive(false);

      // Act + Assert
      assertThatThrownBy(() -> service.collectDueRequests(NOW))
          .isInstanceOf(IllegalStateException.class);
      verifyNoInteractions(huntValidationRepository);
    }
  }

  @Nested
  @DisplayName("Recording outcomes")
  class RecordingOutcomes {

    private static final Instant NOW = Instant.parse("2026-10-03T11:00:00Z");

    @BeforeEach
    void setUp() {
      insideTransaction();
    }

    private void givenStored(SecurityCoverageHuntValidation... validations) {
      when(huntValidationRepository.findAllByIdForUpdate(anySet()))
          .thenReturn(List.of(validations));
    }

    @Test
    @DisplayName("given a failure under a claim another delivery took over should not record it")
    void given_failureUnderEndedClaim_should_notRecordIt() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      Instant otherClaim = NOW.plus(Duration.ofMinutes(4));
      validation.setNextAttemptAt(otherClaim);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "EE required", SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
      assertThat(validation.getAttempts()).isEqualTo(1);
      assertThat(validation.getNextAttemptAt()).isEqualTo(otherClaim);
      assertThat(validation.getLastError()).isNull();
      verify(huntValidationRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("given an acceptance after a concurrent give-up should record the acceptance")
    void given_acceptanceAfterGiveUp_should_recordTheAcceptance() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      validation.setStatus(Status.FAILED);
      validation.setNextAttemptAt(NOW.plus(Duration.ofMinutes(4)));
      validation.setLastError("unknown technique");
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.VALIDATED, 1, 2, null, SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.VALIDATED);
      assertThat(validation.getRunsCount()).isEqualTo(2);
      assertThat(validation.getLastError()).isNull();
      verify(huntValidationRepository).saveAll(List.of(validation));
    }

    @Test
    @DisplayName("given a validation that turned stale before its call should give it up")
    void given_expired_should_giveUp() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      validation.setLastError("Connection refused");
      givenStored(validation);
      String reason =
          "Not delivered within " + SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE;

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.EXPIRED, null, null, reason, SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
      assertThat(validation.getAttempts()).isEqualTo(1);
      assertThat(validation.getLastError()).isEqualTo(reason + "; last error: Connection refused");
    }

    @Test
    @DisplayName("given a validation not tried in the run should release it without an attempt")
    void given_deferred_should_releaseWithoutAttempt() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1",
                  HuntValidationOutcome.Kind.DEFERRED,
                  null,
                  null,
                  "Not tried: the delivery budget of the run was spent",
                  SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
      assertThat(validation.getAttempts()).isEqualTo(1);
      assertThat(validation.getNextAttemptAt()).isEqualTo(NOW);
      assertThat(validation.getLastError()).isNull();
    }

    @Test
    @DisplayName("given an accepted validation should mark it validated with its counts")
    void given_accepted_should_markValidated() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      validation.setLastError("previous refusal");
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.VALIDATED, 2, 3, null, SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.VALIDATED);
      assertThat(validation.getAttempts()).isEqualTo(2);
      assertThat(validation.getHuntsCount()).isEqualTo(2);
      assertThat(validation.getRunsCount()).isEqualTo(3);
      assertThat(validation.getValidatedAt()).isEqualTo(NOW);
      assertThat(validation.getLastError()).isNull();
      verify(huntValidationRepository).saveAll(List.of(validation));
    }

    @Test
    @DisplayName("given a refusal below the maximum should retry after the backoff")
    void given_refusalBelowMaximum_should_retryAfterBackoff() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "EE required", SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
      assertThat(validation.getAttempts()).isEqualTo(2);
      assertThat(validation.getNextAttemptAt())
          .isEqualTo(NOW.plus(RETRY_BASE_DELAY.multipliedBy(2)));
      assertThat(validation.getLastError()).isEqualTo("EE required");
    }

    @Test
    @DisplayName("given the last allowed refusal should give the validation up")
    void given_lastAllowedRefusal_should_giveUp() {
      // Arrange
      SecurityCoverageHuntValidation validation =
          pendingValidation("1", SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS - 1);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "unknown technique", SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
      assertThat(validation.getAttempts())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("given OpenCTI unreachable should postpone without spending an attempt")
    void given_unreachable_should_postponeWithoutSpendingAttempt() {
      // Arrange
      SecurityCoverageHuntValidation validation =
          pendingValidation("1", SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS - 1);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Connection refused",
                  SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
      assertThat(validation.getAttempts())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS - 1);
      assertThat(validation.getNextAttemptAt()).isEqualTo(NOW.plus(RETRY_BASE_DELAY));
      assertThat(validation.getLastError()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("given an outage outliving the maximum age should give the validation up")
    void given_outageOutlivingMaxAge_should_giveUp() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 0);
      validation.setCreatedAt(NOW.minus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE));
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Connection refused",
                  SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
      assertThat(validation.getAttempts()).isZero();
      assertThat(validation.getLastError()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("given an outage younger than the maximum age should keep postponing")
    void given_outageYoungerThanMaxAge_should_keepPostponing() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 0);
      validation.setCreatedAt(
          NOW.minus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE).plusSeconds(1));
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1",
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Connection refused",
                  SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
      assertThat(validation.getNextAttemptAt()).isEqualTo(NOW.plus(RETRY_BASE_DELAY));
    }

    @Test
    @DisplayName("given a refusal past the maximum age should give up before the last attempt")
    void given_refusalPastMaxAge_should_giveUpBeforeLastAttempt() {
      // Arrange
      config.setMaxAge(Duration.ofHours(1));
      SecurityCoverageHuntValidation validation = pendingValidation("1", 1);
      validation.setCreatedAt(NOW.minus(Duration.ofHours(2)));
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "unknown technique", SENT)),
          NOW);

      // Assert
      assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
      assertThat(validation.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("given a validation settled meanwhile should leave it untouched")
    void given_settledMeanwhile_should_leaveUntouched() {
      // Arrange
      SecurityCoverageHuntValidation validated = pendingValidation("1", 1);
      validated.setStatus(Status.VALIDATED);
      givenStored(validated);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "late", SENT)),
          NOW);

      // Assert
      assertThat(validated.getStatus()).isEqualTo(Status.VALIDATED);
      assertThat(validated.getAttempts()).isEqualTo(1);
      assertThat(validated.getLastError()).isNull();
    }

    @Test
    @DisplayName("given a very long error should store it truncated")
    void given_veryLongError_should_storeItTruncated() {
      // Arrange
      SecurityCoverageHuntValidation validation = pendingValidation("1", 0);
      givenStored(validation);

      // Act
      service.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  "1", HuntValidationOutcome.Kind.REFUSED, null, null, "x".repeat(5000), SENT)),
          NOW);

      // Assert
      assertThat(validation.getLastError()).hasSize(MAX_ERROR_LENGTH);
    }

    @Test
    @DisplayName("given no outcome should not touch the database")
    void given_noOutcome_should_notTouchDatabase() {
      // Act
      service.recordOutcomes(List.of(), NOW);

      // Assert
      verify(huntValidationRepository, never()).findAllByIdForUpdate(anySet());
    }
  }

  @Nested
  @DisplayName("Configuration")
  class Configuration {

    @Test
    @DisplayName("given the default configuration should be disabled")
    void given_defaultConfiguration_should_beDisabled() {
      // Act + Assert
      assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("given hunt validation enabled should report it")
    void given_enabled_should_reportIt() {
      // Arrange
      config.setEnabled(true);

      // Act + Assert
      assertThat(service.isEnabled()).isTrue();
    }
  }
}
