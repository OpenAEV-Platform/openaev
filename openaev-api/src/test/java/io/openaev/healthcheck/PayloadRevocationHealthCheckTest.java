package io.openaev.healthcheck;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("A revoked payload version shows in the health checks")
class PayloadRevocationHealthCheckTest extends IntegrationTest {

  @Autowired private HealthCheckUtils healthCheckUtils;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;
  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;

  @BeforeEach
  void resetComposers() {
    exerciseComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    injectStatusComposer.reset();
    executionTraceComposer.reset();
    injectExpectationComposer.reset();
  }

  private Inject injectRunning(boolean revoked, boolean enabled) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    payload = payloadRepository.save(payload);
    if (revoked) {
      PayloadRevocation revocation = new PayloadRevocation();
      revocation.setTenant(new Tenant(DEFAULT_TENANT_UUID));
      revocation.setPayload(payload);
      revocation.setReason("Deletes user files");
      payloadRevocationRepository.save(revocation);
    }
    InjectorContract contract = new InjectorContract();
    contract.setPayload(payload);
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(contract);
    inject.setEnabled(enabled);
    return inject;
  }

  @ParameterizedTest(name = "revoked {0}, enabled {1}: {2} check")
  @CsvSource({"true, true, 1", "true, false, 0", "false, true, 0"})
  @DisplayName("only an enabled inject running a revoked version raises a check")
  void given_inject_should_raiseARevokedCheckOnlyWhenItWouldRunARevokedVersion(
      boolean revoked, boolean enabled, int checks) {
    // Arrange
    Inject inject = injectRunning(revoked, enabled);

    // Act
    List<HealthCheck> result = healthCheckUtils.runRevocationChecks(List.of(inject));

    // Assert
    assertThat(result)
        .extracting(HealthCheck::getType, HealthCheck::getDetail, HealthCheck::getStatus)
        .containsExactlyElementsOf(
            Collections.nCopies(
                checks,
                tuple(
                    HealthCheck.Type.PAYLOAD,
                    HealthCheck.Detail.REVOKED,
                    HealthCheck.Status.ERROR)));
  }

  /**
   * A persisted simulation whose inject runs a payload version, with the status, trace and
   * expectation a run leaves behind: what loading the inject graph would hydrate.
   */
  private String simulationRunning(boolean revoked, boolean enabled) {
    PayloadComposer.Composer payload =
        payloadComposer.forPayload(PayloadFixture.createDefaultCommand());
    Inject inject = InjectFixture.getDefaultInject();
    inject.setEnabled(enabled);
    String simulationId =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(
                injectComposer
                    .forInject(inject)
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                            .withPayload(payload))
                    .withInjectStatus(
                        injectStatusComposer
                            .forInjectStatus(InjectStatusFixture.createSuccessStatus())
                            .withExecutionTrace(
                                executionTraceComposer.forExecutionTrace(
                                    ExecutionTraceFixture.createDefaultExecutionTraceComplete())))
                    .withExpectation(
                        injectExpectationComposer.forExpectation(
                            InjectExpectationFixture.createDefaultDetectionInjectExpectation())))
            .persist()
            .get()
            .getId();
    if (revoked) {
      Payload saved = payload.get();
      PayloadRevocation revocation = new PayloadRevocation();
      revocation.setTenant(saved.getTenant());
      revocation.setPayload(saved);
      revocation.setReason("Deletes user files");
      payloadRevocationRepository.save(revocation);
    }
    entityManager.flush();
    entityManager.clear();
    return simulationId;
  }

  @ParameterizedTest(name = "revoked {0}, enabled {1}: {2} check")
  @CsvSource({"true, true, 1", "true, false, 0", "false, true, 0"})
  @DisplayName("a simulation raises the same check as its injects, without loading them")
  void given_simulation_should_raiseTheSameRevokedCheckWithoutLoadingItsInjects(
      boolean revoked, boolean enabled, int checks) {
    // Arrange
    String simulationId = simulationRunning(revoked, enabled);
    List<HealthCheck> fromInjects =
        healthCheckUtils.runRevocationChecks(injectRepository.findByExerciseId(simulationId));
    entityManager.clear();
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    // The statistics belong to the session factory shared by every test: put them back as found
    boolean statisticsWereEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    stats.clear();

    // Act
    List<HealthCheck> result;
    long statements;
    try {
      result = exerciseService.runChecks(simulationId);
      statements = stats.getPrepareStatementCount();
    } finally {
      stats.setStatisticsEnabled(statisticsWereEnabled);
    }

    // Assert
    assertThat(result)
        .extracting(HealthCheck::getType, HealthCheck::getDetail, HealthCheck::getStatus)
        .containsExactlyElementsOf(
            Collections.nCopies(
                checks,
                tuple(
                    HealthCheck.Type.PAYLOAD,
                    HealthCheck.Detail.REVOKED,
                    HealthCheck.Status.ERROR)))
        .containsExactlyElementsOf(
            fromInjects.stream()
                .map(check -> tuple(check.getType(), check.getDetail(), check.getStatus()))
                .toList());
    for (Class<?> graphEntity : List.of(Inject.class, InjectStatus.class, ExecutionTrace.class)) {
      assertThat(stats.getEntityStatistics(graphEntity.getName()).getLoadCount())
          .as("%s loaded", graphEntity.getSimpleName())
          .isZero();
    }
    assertThat(stats.getEntityLoadCount()).as("entities loaded").isZero();
    // One statement for the revocation, one for the workflow template lookup
    assertThat(statements).isLessThanOrEqualTo(2);
  }
}
