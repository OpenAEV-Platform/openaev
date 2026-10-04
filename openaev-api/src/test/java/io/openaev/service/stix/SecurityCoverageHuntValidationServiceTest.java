package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.SecurityCoverageHuntValidation;
import io.openaev.database.model.SecurityCoverageHuntValidation.Status;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageHuntValidationRepository;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationRequest;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.SecurityCoverageComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hunt validation outbox against the real schema: what a finished simulation plans, how a rerun
 * deduplicates, how delivery outcomes settle a row, and what the migration enforces (unique key,
 * cascade on inject deletion).
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("SecurityCoverageHuntValidationService")
class SecurityCoverageHuntValidationServiceTest extends IntegrationTest {

  private static final Instant SENT = Instant.parse("2026-10-03T10:00:00Z");
  private static final Instant ENDED = Instant.parse("2026-10-03T10:12:00Z");
  private static final Duration PADDING =
      SecurityCoverageHuntValidationConfig.DEFAULT_WINDOW_PADDING;

  @Autowired private SecurityCoverageHuntValidationService huntValidationService;
  @Autowired private SecurityCoverageHuntValidationRepository huntValidationRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private InjectorFixture injectorFixture;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    injectExpectationComposer.reset();
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    securityCoverageComposer.reset();
    securityPlatformComposer.reset();
  }

  /** What a test seeded: the simulation, its single inject, its techniques and its platform. */
  private record Seeded(
      String simulationId,
      String injectId,
      String tenantId,
      String coverageExternalId,
      SecurityPlatform platform,
      List<String> techniqueIds) {}

  private SecurityPlatform platform(SecurityPlatform.SECURITY_PLATFORM_TYPE type) {
    return securityPlatformComposer
        .forSecurityPlatform(
            SecurityPlatformFixture.createDefault("Platform " + UUID.randomUUID(), type.name()))
        .persist()
        .get();
  }

  private Seeded seedFinishedSimulation(
      SecurityPlatform platform,
      ExecutionStatus injectStatus,
      Double platformScore,
      int techniques) {
    InjectorContractComposer.Composer contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector());
    List<String> techniqueIds = new ArrayList<>();
    for (int i = 0; i < techniques; i++) {
      AttackPatternComposer.Composer technique =
          attackPatternComposer.forAttackPattern(AttackPatternFixture.createDefaultAttackPattern());
      techniqueIds.add(technique.get().getExternalId());
      contract.withAttackPattern(technique);
    }

    InjectStatus status = InjectStatusFixture.createSuccessStatus();
    status.setName(injectStatus);
    status.setTrackingSentDate(SENT);
    status.setTrackingEndDate(ENDED);

    BaseInjectExpectation detection =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(
            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
            BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
    detection.setResults(new ArrayList<>(List.of(platformResult(platform, platformScore))));

    SecurityCoverage coverage = SecurityCoverageFixture.createDefaultSecurityCoverage();
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(contract)
            .withInjectStatus(injectStatusComposer.forInjectStatus(status))
            .withExpectation(injectExpectationComposer.forExpectation(detection));
    ExerciseComposer.Composer simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createFinishedAttackExercise())
            .withSecurityCoverage(securityCoverageComposer.forSecurityCoverage(coverage))
            .withInject(inject)
            .persist();
    entityManager.flush();
    entityManager.clear();
    return new Seeded(
        simulation.get().getId(),
        inject.get().getId(),
        simulation.get().getTenant().getId(),
        coverage.getExternalId(),
        platform,
        techniqueIds);
  }

  private static InjectExpectationResult platformResult(SecurityPlatform platform, Double score) {
    return InjectExpectationResult.builder()
        .sourceId(UUID.randomUUID().toString())
        .sourceType("collector")
        .sourceName(platform.getName())
        .sourcePlatform(platform.getSecurityPlatformType().name())
        .sourceAssetId(platform.getId())
        .score(score)
        .result(score == null ? null : "Detected")
        .build();
  }

  private List<SecurityCoverageHuntValidation> validationsOf(String injectId) {
    return huntValidationRepository.findAllByInjectIdIn(List.of(injectId));
  }

  private List<HuntValidationRequest> dueRequestsOf(String injectId) {
    return dueRequestsOf(injectId, Instant.now());
  }

  private List<HuntValidationRequest> dueRequestsOf(String injectId, Instant now) {
    return huntValidationService.collectDueRequests(now).stream()
        .filter(request -> request.injectId().equals(injectId))
        .toList();
  }

  @Nested
  @DisplayName("Planning")
  class Planning {

    @Test
    @DisplayName("given a computed platform verdict should plan one validation per technique")
    void given_computedVerdict_should_planOneValidationPerTechnique() {
      // Arrange
      SecurityPlatform edr = platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      Seeded seeded = seedFinishedSimulation(edr, ExecutionStatus.EXECUTED, 100.0, 2);

      // Act
      int planned = huntValidationService.planForSimulation(seeded.simulationId());

      // Assert
      assertThat(planned).isEqualTo(2);
      List<SecurityCoverageHuntValidation> validations = validationsOf(seeded.injectId());
      assertThat(validations)
          .extracting(SecurityCoverageHuntValidation::getTechniqueId)
          .containsExactlyInAnyOrderElementsOf(seeded.techniqueIds());
      assertThat(validations)
          .allSatisfy(
              validation -> {
                assertThat(validation.getSecurityPlatformId()).isEqualTo(edr.getId());
                assertThat(validation.getSecurityPlatformName()).isEqualTo(edr.getName());
                assertThat(validation.getCoverageExternalId())
                    .isEqualTo(seeded.coverageExternalId());
                assertThat(validation.getWindowStart()).isEqualTo(SENT.minus(PADDING));
                assertThat(validation.getWindowEnd()).isEqualTo(ENDED.plus(PADDING));
                assertThat(validation.getStatus()).isEqualTo(Status.PENDING);
                assertThat(validation.getAttempts()).isZero();
                assertThat(validation.getTenant().getId()).isEqualTo(seeded.tenantId());
                assertThat(validation.getCreatedAt()).isNotNull();
                assertThat(validation.getUpdatedAt()).isNotNull();
              });
    }

    @Test
    @DisplayName("given the coverage job rerunning should never plan a triple twice")
    void given_rerun_should_neverPlanTwice() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM),
              ExecutionStatus.EXECUTED,
              0.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      entityManager.flush();
      entityManager.clear();

      // Act
      int replanned = huntValidationService.planForSimulation(seeded.simulationId());

      // Assert
      assertThat(replanned).isZero();
      assertThat(validationsOf(seeded.injectId())).hasSize(1);
    }

    @Test
    @DisplayName("given a pending verdict should wait for it, then plan once it is computed")
    void given_pendingVerdict_should_waitThenPlan() {
      // Arrange
      SecurityPlatform edr = platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
      Seeded seeded = seedFinishedSimulation(edr, ExecutionStatus.EXECUTED, null, 1);

      // Act
      int plannedWhilePending = huntValidationService.planForSimulation(seeded.simulationId());
      BaseInjectExpectation expectation =
          injectExpectationRepository.findAllByInjectId(seeded.injectId()).getFirst();
      expectation.setResults(new ArrayList<>(List.of(platformResult(edr, 0.0))));
      injectExpectationRepository.save(expectation);
      entityManager.flush();
      entityManager.clear();
      int plannedOnceComputed = huntValidationService.planForSimulation(seeded.simulationId());

      // Assert
      assertThat(plannedWhilePending).isZero();
      assertThat(plannedOnceComputed).isEqualTo(1);
    }

    @Test
    @DisplayName("given an inject that failed to run should plan nothing")
    void given_injectInError_should_planNothing() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.ERROR,
              100.0,
              1);

      // Act + Assert
      assertThat(huntValidationService.planForSimulation(seeded.simulationId())).isZero();
      assertThat(validationsOf(seeded.injectId())).isEmpty();
    }

    @Test
    @DisplayName("given a platform type OpenCTI hunts cannot run on should plan nothing")
    void given_emailSecurityPlatform_should_planNothing() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EMAIL_SECURITY),
              ExecutionStatus.EXECUTED,
              100.0,
              1);

      // Act + Assert
      assertThat(huntValidationService.planForSimulation(seeded.simulationId())).isZero();
      assertThat(validationsOf(seeded.injectId())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Delivery bookkeeping")
  class DeliveryBookkeeping {

    @Test
    @DisplayName("given a planned validation should be due with the OpenCTI input of the contract")
    void given_plannedValidation_should_beDueWithContractInput() {
      // Arrange
      SecurityPlatform xdr = platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.XDR);
      Seeded seeded = seedFinishedSimulation(xdr, ExecutionStatus.PARTIAL, 100.0, 1);
      huntValidationService.planForSimulation(seeded.simulationId());

      // Act
      List<HuntValidationRequest> due = dueRequestsOf(seeded.injectId());

      // Assert
      assertThat(due).hasSize(1);
      assertThat(due.getFirst().toInput())
          .satisfies(
              input -> {
                assertThat(input.techniqueId()).isEqualTo(seeded.techniqueIds().getFirst());
                assertThat(input.securityPlatformId())
                    .isEqualTo(xdr.toStixDomainObject().getId().getValue());
                assertThat(input.securityPlatformName()).isEqualTo(xdr.getName());
                assertThat(input.injectId()).isEqualTo(seeded.injectId());
                assertThat(input.windowStart()).isEqualTo(SENT.minus(PADDING).toString());
                assertThat(input.windowEnd()).isEqualTo(ENDED.plus(PADDING).toString());
                assertThat(input.securityCoverageId()).isEqualTo(seeded.coverageExternalId());
              });
    }

    @Test
    @DisplayName("given OpenCTI accepted the validation should never be due again")
    void given_accepted_should_neverBeDueAgain() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      HuntValidationRequest request = dueRequestsOf(seeded.injectId()).getFirst();
      String validationId = request.id();

      // Act
      huntValidationService.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  validationId,
                  HuntValidationOutcome.Kind.VALIDATED,
                  1,
                  2,
                  null,
                  request.leaseUntil())),
          Instant.now());
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(dueRequestsOf(seeded.injectId())).isEmpty();
      SecurityCoverageHuntValidation validation =
          huntValidationRepository.findById(validationId).orElseThrow();
      assertThat(validation.getStatus()).isEqualTo(Status.VALIDATED);
      assertThat(validation.getHuntsCount()).isEqualTo(1);
      assertThat(validation.getRunsCount()).isEqualTo(2);
      assertThat(validation.getValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("given OpenCTI refusing every attempt should give the validation up")
    void given_everyAttemptRefused_should_giveUp() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      String validationId = null;

      // Act: every attempt is a new delivery, once the backoff of the previous refusal has passed
      Instant now = Instant.now();
      for (int attempt = 0;
          attempt < SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS;
          attempt++) {
        HuntValidationRequest request = dueRequestsOf(seeded.injectId(), now).getFirst();
        validationId = request.id();
        huntValidationService.recordOutcomes(
            List.of(
                new HuntValidationOutcome(
                    validationId,
                    HuntValidationOutcome.Kind.REFUSED,
                    null,
                    null,
                    "Enterprise edition is not enabled",
                    request.leaseUntil())),
            now);
        now = now.plus(SecurityCoverageHuntValidationService.RETRY_MAX_DELAY);
      }
      entityManager.flush();
      entityManager.clear();

      // Assert
      SecurityCoverageHuntValidation validation =
          huntValidationRepository.findById(validationId).orElseThrow();
      assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
      assertThat(validation.getAttempts())
          .isEqualTo(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_ATTEMPTS);
      assertThat(validation.getLastError()).isEqualTo("Enterprise edition is not enabled");
      assertThat(dueRequestsOf(seeded.injectId(), now)).isEmpty();
    }

    @Test
    @DisplayName("given a claimed validation should not be claimed again before its claim ends")
    void given_claimedValidation_should_notBeClaimedAgainBeforeItsClaimEnds() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      Instant now = Instant.now();
      HuntValidationRequest claimed = dueRequestsOf(seeded.injectId(), now).getFirst();
      entityManager.flush();
      entityManager.clear();

      // Act
      List<HuntValidationRequest> beforeTheEnd =
          dueRequestsOf(seeded.injectId(), claimed.leaseUntil().minusMillis(1));
      List<HuntValidationRequest> atTheEnd = dueRequestsOf(seeded.injectId(), claimed.leaseUntil());

      // Assert
      assertThat(claimed.leaseUntil())
          .isEqualTo(now.plus(huntValidationService.claimLease()).truncatedTo(ChronoUnit.MILLIS));
      assertThat(beforeTheEnd).isEmpty();
      assertThat(atTheEnd).extracting(HuntValidationRequest::id).containsExactly(claimed.id());
      assertThat(atTheEnd.getFirst().leaseUntil()).isAfter(claimed.leaseUntil());
    }

    @Test
    @DisplayName(
        "given a claim taken over by another delivery should record only that delivery's failure")
    void given_claimTakenOver_should_recordOnlyTheNewClaimFailure() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      Instant now = Instant.now();
      HuntValidationRequest first = dueRequestsOf(seeded.injectId(), now).getFirst();
      HuntValidationRequest second =
          dueRequestsOf(seeded.injectId(), first.leaseUntil()).getFirst();

      // Act
      huntValidationService.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  first.id(),
                  HuntValidationOutcome.Kind.UNREACHABLE,
                  null,
                  null,
                  "Read timed out",
                  first.leaseUntil())),
          second.leaseUntil());
      entityManager.flush();
      entityManager.clear();

      // Assert: the failure of the ended claim is not recorded
      SecurityCoverageHuntValidation afterStaleFailure =
          huntValidationRepository.findById(first.id()).orElseThrow();
      assertThat(afterStaleFailure.getStatus()).isEqualTo(Status.PENDING);
      assertThat(afterStaleFailure.getNextAttemptAt()).isEqualTo(second.leaseUntil());
      assertThat(afterStaleFailure.getLastError()).isNull();
      entityManager.clear();

      // Act: the acceptance of the first delivery arrives late
      huntValidationService.recordOutcomes(
          List.of(
              new HuntValidationOutcome(
                  first.id(),
                  HuntValidationOutcome.Kind.VALIDATED,
                  1,
                  1,
                  null,
                  first.leaseUntil())),
          second.leaseUntil());
      entityManager.flush();
      entityManager.clear();

      // Assert: an acceptance always wins
      assertThat(huntValidationRepository.findById(first.id()).orElseThrow().getStatus())
          .isEqualTo(Status.VALIDATED);
    }

    @Test
    @DisplayName("given a due validation older than the maximum age should give it up unsent")
    void given_dueValidationPastMaxAge_should_giveUpUnsent() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      entityManager.flush();
      entityManager
          .createNativeQuery(
              "UPDATE security_coverage_hunt_validations"
                  + " SET security_coverage_hunt_validation_created_at = :createdAt"
                  + " WHERE security_coverage_hunt_validation_inject_id = :injectId")
          .setParameter(
              "createdAt",
              java.sql.Timestamp.from(
                  Instant.now()
                      .minus(SecurityCoverageHuntValidationConfig.DEFAULT_MAX_AGE)
                      .minusSeconds(60)))
          .setParameter("injectId", seeded.injectId())
          .executeUpdate();
      entityManager.clear();

      // Act
      List<HuntValidationRequest> due = dueRequestsOf(seeded.injectId());
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(due).isEmpty();
      assertThat(validationsOf(seeded.injectId()))
          .singleElement()
          .satisfies(
              validation -> {
                assertThat(validation.getStatus()).isEqualTo(Status.FAILED);
                assertThat(validation.getAttempts()).isZero();
                assertThat(validation.getValidatedAt()).isNull();
                assertThat(validation.getLastError()).startsWith("Not delivered within");
              });
    }
  }

  @Nested
  @DisplayName("Schema")
  class Schema {

    @Test
    @DisplayName("given the inject deleted should delete its validations with it")
    void given_injectDeleted_should_cascadeToValidations() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      entityManager.flush();
      entityManager.clear();

      // Act
      Inject inject = injectRepository.findById(seeded.injectId()).orElseThrow();
      injectRepository.delete(inject);
      entityManager.flush();
      entityManager.clear();

      // Assert
      Number remaining =
          (Number)
              entityManager
                  .createNativeQuery(
                      "SELECT count(*) FROM security_coverage_hunt_validations"
                          + " WHERE security_coverage_hunt_validation_inject_id = :injectId")
                  .setParameter("injectId", seeded.injectId())
                  .getSingleResult();
      assertThat(remaining.longValue()).isZero();
    }

    @Test
    @DisplayName("given the same triple stored twice in a tenant should be refused by the schema")
    void given_duplicateTriple_should_beRefused() {
      // Arrange
      Seeded seeded =
          seedFinishedSimulation(
              platform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
              ExecutionStatus.EXECUTED,
              100.0,
              1);
      huntValidationService.planForSimulation(seeded.simulationId());
      entityManager.flush();
      SecurityCoverageHuntValidation planned = validationsOf(seeded.injectId()).getFirst();
      SecurityCoverageHuntValidation duplicate = new SecurityCoverageHuntValidation();
      duplicate.setTenant(new Tenant(seeded.tenantId()));
      duplicate.setInjectId(planned.getInjectId());
      duplicate.setTechniqueId(planned.getTechniqueId());
      duplicate.setSecurityPlatformId(planned.getSecurityPlatformId());
      duplicate.setSecurityPlatformName(planned.getSecurityPlatformName());
      duplicate.setCoverageExternalId(planned.getCoverageExternalId());
      duplicate.setWindowStart(planned.getWindowStart());
      duplicate.setWindowEnd(planned.getWindowEnd());
      duplicate.setNextAttemptAt(Instant.now());

      // Act + Assert
      assertThatThrownBy(() -> huntValidationRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("idx_security_coverage_hunt_validations_key_tenant_uq");
    }
  }
}
