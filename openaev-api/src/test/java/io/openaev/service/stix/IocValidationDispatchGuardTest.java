package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IocValidationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IOC validation dispatch guard")
class IocValidationDispatchGuardTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String SIMULATION_ID = "simulation-1";

  @Mock private IocValidationRepository iocValidationRepository;

  @InjectMocks private IocValidationDispatchGuard guard;

  @Test
  @DisplayName("an approved test of the validation runs in its simulation")
  void given_approvedTest_should_run() {
    when(iocValidationRepository.findBySimulationIdAndTenantId(SIMULATION_ID, TENANT_ID))
        .thenReturn(List.of(validation("inject-test")));

    assertThatCode(() -> guard.refuseInjectOutsideApproval(inject("inject-test", SIMULATION_ID)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an inject added to the simulation of a validation after its approval never runs")
  void given_injectAddedToTheValidationSimulation_should_notRun() {
    when(iocValidationRepository.findBySimulationIdAndTenantId(SIMULATION_ID, TENANT_ID))
        .thenReturn(List.of(validation("inject-test")));

    assertThatThrownBy(
            () -> guard.refuseInjectOutsideApproval(inject("inject-added", SIMULATION_ID)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(IocValidationDispatchGuard.IOC_VALIDATION_INJECT_NOT_APPROVED);
  }

  @Test
  @DisplayName("an inject of a simulation that is no IOC validation runs")
  void given_otherSimulation_should_run() {
    when(iocValidationRepository.findBySimulationIdAndTenantId("simulation-2", TENANT_ID))
        .thenReturn(List.of());

    assertThatCode(() -> guard.refuseInjectOutsideApproval(inject("inject-1", "simulation-2")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an atomic testing is no IOC validation")
  void given_atomicTesting_should_run() {
    assertThatCode(() -> guard.refuseInjectOutsideApproval(inject("inject-1", null)))
        .doesNotThrowAnyException();
    verifyNoInteractions(iocValidationRepository);
  }

  private static Inject inject(String id, String simulationId) {
    Inject inject = new Inject();
    inject.setId(id);
    inject.setTenant(new Tenant(TENANT_ID));
    if (simulationId != null) {
      Exercise simulation = new Exercise();
      simulation.setId(simulationId);
      inject.setExercise(simulation);
    }
    return inject;
  }

  private static IocValidation validation(String testInjectId) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setInjectIds(new ArrayList<>(List.of(testInjectId)));
    IocValidation validation = new IocValidation();
    validation.setSimulationId(SIMULATION_ID);
    validation.setIocs(new ArrayList<>(List.of(ioc)));
    return validation;
  }
}
