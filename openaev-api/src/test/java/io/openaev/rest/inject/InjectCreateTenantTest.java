package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AtomicTestingService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * An inject is written in the tenant of its owner: the simulation or the scenario it is created
 * under, or the single tenant its request pins when it has no owner. It never takes the ambient
 * tenant, which the non-prefixed route leaves on the default one while the owner lives in another
 * tenant: a caller of that tenant only would then not find its own inject by id.
 *
 * <p>Each case runs with the ambient tenant cleared, as production's request thread starts, and the
 * owner in a tenant that is not the default one.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("An inject is created in the tenant of its owner, not in the ambient one")
class InjectCreateTenantTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectService injectService;
  @Autowired private AtomicTestingService atomicTestingService;
  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;

  private String ownerTenant;
  private String title;
  private String contractId;

  @BeforeEach
  void createOwnerTenant() throws Exception {
    ownerTenant = tenantHelper.createTenantWithCurrentUser("inject-owner-tenant").getId();
    title = "inject-of-owner-tenant-" + UUID.randomUUID();
    contractId = injectorContractFixture.getWellKnownSingleEmailContract().getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private InjectInput input() {
    InjectInput input = new InjectInput();
    input.setTitle(title);
    input.setInjectorContract(contractId);
    input.setDependsDuration(0L);
    return input;
  }

  private void clearAmbientTenantAndCheckFallback() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private Exercise saveExercise() {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(ownerTenant));
    return exerciseRepository.save(exercise);
  }

  private Scenario saveScenario() {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(ownerTenant));
    return scenarioRepository.save(scenario);
  }

  private interface Creation {
    List<Inject> create(InjectCreateTenantTest test) throws Exception;
  }

  static Stream<Arguments> creationsUnderAnOwner() {
    return Stream.of(
        Arguments.of(
            "a simulation, one inject",
            (Creation)
                test ->
                    List.of(
                        test.injectService.createAndSaveInject(
                            TxCtx.forTenant(test.ownerTenant),
                            test.saveExercise(),
                            null,
                            test.input()))),
        Arguments.of(
            "a simulation, several injects",
            (Creation)
                test ->
                    test.injectService.createAndSaveInjectList(
                        TxCtx.forTenant(test.ownerTenant),
                        test.saveExercise(),
                        null,
                        List.of(test.input()))),
        Arguments.of(
            "a scenario, one inject",
            (Creation)
                test ->
                    List.of(
                        test.injectService.createAndSaveInject(
                            TxCtx.forTenant(test.ownerTenant),
                            null,
                            test.saveScenario(),
                            test.input()))),
        Arguments.of(
            "a scenario, several injects",
            (Creation)
                test ->
                    test.injectService.createAndSaveInjectList(
                        TxCtx.forTenant(test.ownerTenant),
                        null,
                        test.saveScenario(),
                        List.of(test.input()))));
  }

  @Nested
  @DisplayName("Injects created under a simulation or a scenario")
  class UnderAnOwner {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.inject.InjectCreateTenantTest#creationsUnderAnOwner")
    @DisplayName("given an owner in another tenant, should write the inject in the owner's tenant")
    void given_ownerInAnotherTenant_should_writeInjectInOwnerTenant(String name, Creation creation)
        throws Exception {
      // Arrange
      clearAmbientTenantAndCheckFallback();

      // Act
      List<Inject> created = creation.create(InjectCreateTenantTest.this);

      // Assert
      assertThat(created).as(name).hasSize(1);
      assertThat(created.getFirst().getTenant().getId()).as(name).isEqualTo(ownerTenant);
      assertThat(
              injectService.findInScope(TxCtx.forTenant(ownerTenant), created.getFirst().getId()))
          .as(name + " read by id in the owner's scope")
          .isPresent();
    }
  }

  @Nested
  @DisplayName("Injects copied from a scenario into a simulation")
  class CopiedFromAScenario {

    @Test
    @DisplayName("given a scenario in another tenant, should write the copies in that tenant")
    void given_scenarioInAnotherTenant_should_writeSimulationInjectsInThatTenant() {
      // Arrange
      Scenario scenario = saveScenario();
      Inject scenarioInject =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      scenarioInject.setTitle(title);
      InjectUtils.attachToScenario(scenarioInject, scenario);
      injectRepository.save(scenarioInject);
      scenario.setInjects(new HashSet<>(Set.of(scenarioInject)));
      entityManager.flush();
      clearAmbientTenantAndCheckFallback();

      // Act
      Exercise simulation = scenarioToExerciseService.toExercise(scenario, null, false, null);

      // Assert
      entityManager.flush();
      // Raw SQL: a repository read would run under the ambient tenant filter, the default one here
      @SuppressWarnings("unchecked")
      List<String> tenants =
          entityManager
              .createNativeQuery("SELECT tenant_id FROM injects WHERE inject_exercise = :id")
              .setParameter("id", simulation.getId())
              .getResultList();
      assertThat(tenants).containsExactly(ownerTenant);
    }
  }

  @Nested
  @DisplayName("Atomic testings, which have no owner")
  class AtomicTestings {

    @Test
    @DisplayName("given a write tenant, should write the atomic testing in that tenant")
    void given_writeTenant_should_writeAtomicTestingInThatTenant() {
      // Arrange
      AtomicTestingInput input = InjectFixture.createAtomicTesting(title, null);
      input.setInjectorContract(contractId);
      clearAmbientTenantAndCheckFallback();

      // Act
      var created =
          atomicTestingService.createOrUpdate(
              TxCtx.forTenant(ownerTenant), ownerTenant, input, null);

      // Assert
      entityManager.flush();
      assertThat(injectRepository.findById(created.getId()).orElseThrow().getTenant().getId())
          .isEqualTo(ownerTenant);
    }
  }
}
