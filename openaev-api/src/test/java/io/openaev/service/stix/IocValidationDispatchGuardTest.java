package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Command;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.rest.payload.service.PayloadService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private static final String USER_PAYLOAD_ID = "4b3f8e52-2f1b-4c36-9a4e-1d2c3b4a5f60";

  @Mock private IocValidationRepository iocValidationRepository;

  @InjectMocks private IocValidationDispatchGuard guard;

  @Test
  @DisplayName("an inject added to the simulation of a validation after its approval never runs")
  void given_injectAddedToTheValidationSimulation_should_notRun() {
    when(iocValidationRepository.findBySimulationIdAndTenantId(SIMULATION_ID, TENANT_ID))
        .thenReturn(List.of(validation("inject-test")));

    assertThatThrownBy(
            () ->
                guard.refuseUnapprovedExecution(
                    inject("inject-added", SIMULATION_ID), userPayload()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(IocValidationDispatchGuard.IOC_VALIDATION_INJECT_NOT_APPROVED);
  }

  @Test
  @DisplayName("an approved test whose contract now runs another injector never runs")
  void given_approvedTestMovedToAnotherInjector_should_notRun() {
    when(iocValidationRepository.findBySimulationIdAndTenantId(SIMULATION_ID, TENANT_ID))
        .thenReturn(List.of(validation("inject-test")));
    Inject test = inject("inject-test", SIMULATION_ID);

    // A contract without a payload (an email, an HTTP request...) or with a payload of the user
    assertThatThrownBy(() -> guard.refuseUnapprovedExecution(test, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(IocValidationDispatchGuard.IOC_VALIDATION_UNAPPROVED_TEST);
    assertThatThrownBy(() -> guard.refuseUnapprovedExecution(test, userPayload()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(IocValidationDispatchGuard.IOC_VALIDATION_UNAPPROVED_TEST);
  }

  @Test
  @DisplayName("a test approved without a recorded payload never runs")
  void given_testWithoutRecordedPayload_should_notRun() {
    IocValidation validation = validation("inject-test");
    validation.getIocs().getFirst().setInjectPayloads(null);
    when(iocValidationRepository.findBySimulationIdAndTenantId(SIMULATION_ID, TENANT_ID))
        .thenReturn(List.of(validation));

    assertThatThrownBy(
            () -> guard.refuseUnapprovedExecution(inject("inject-test", SIMULATION_ID), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(IocValidationDispatchGuard.IOC_VALIDATION_UNAPPROVED_TEST);
  }

  @Test
  @DisplayName("an inject of a simulation that is no IOC validation runs")
  void given_otherSimulation_should_run() {
    when(iocValidationRepository.findBySimulationIdAndTenantId("simulation-2", TENANT_ID))
        .thenReturn(List.of());

    assertThatCode(
            () ->
                guard.refuseUnapprovedExecution(inject("inject-1", "simulation-2"), userPayload()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an atomic testing is no IOC validation")
  void given_atomicTesting_should_run() {
    assertThatCode(() -> guard.refuseUnapprovedExecution(inject("inject-1", null), null))
        .doesNotThrowAnyException();
    verifyNoInteractions(iocValidationRepository);
  }

  @Test
  @DisplayName("an IOC validation file drop payload of an earlier version never runs")
  void given_outdatedIocValidationFileDrop_should_notRun() {
    // The singleton as an earlier version created it, writing directly in the temp dir
    Command legacy =
        iocValidationCommand(
            IocValidationTestKind.FILE_DROP,
            "printf 'OpenAEV IOC validation benign surrogate\\n' > \"${TMPDIR:-/tmp}/\"#{"
                + PayloadService.IOC_VALIDATION_FILE_NAME_KEY
                + "}; true");
    PayloadArgument fileName = new PayloadArgument();
    fileName.setType(PrimitiveType.Text);
    fileName.setKey(PayloadService.IOC_VALIDATION_FILE_NAME_KEY);
    fileName.setDefaultValue("");
    legacy.setArguments(List.of(fileName));
    // A user payload is never concerned, whatever its content
    Command userPayload = userPayload();
    userPayload.setContent(legacy.getContent());

    assertThatThrownBy(() -> IocValidationDispatchGuard.refuseOutdatedIocValidationFileDrop(legacy))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(PayloadService.IOC_VALIDATION_OUTDATED_FILE_DROP);
    assertThatCode(
            () -> IocValidationDispatchGuard.refuseOutdatedIocValidationFileDrop(userPayload))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an IOC validation payload edited after its approval never runs, on any path")
  void given_editedIocValidationPayload_should_notRun() {
    // The network singleton, edited to reach another target than the approved one
    Command edited =
        iocValidationCommand(IocValidationTestKind.NETWORK_TRAFFIC, "nc -z 198.51.100.9 443");
    Command userPayload = userPayload();
    userPayload.setContent(edited.getContent());

    assertThatThrownBy(() -> IocValidationDispatchGuard.refuseEditedIocValidationPayload(edited))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(PayloadService.IOC_VALIDATION_EDITED_PAYLOAD);
    assertThatCode(() -> IocValidationDispatchGuard.refuseEditedIocValidationPayload(userPayload))
        .doesNotThrowAnyException();
    // The approval boundary checks the template before anything else, even outside a validation
    assertThatThrownBy(() -> guard.refuseUnapprovedExecution(inject("inject-1", null), edited))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(PayloadService.IOC_VALIDATION_EDITED_PAYLOAD);
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

  private static Command userPayload() {
    Command payload = new Command();
    payload.setTenant(new Tenant(TENANT_ID));
    payload.setId(USER_PAYLOAD_ID);
    payload.setExecutor(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR);
    return payload;
  }

  private static Command iocValidationCommand(IocValidationTestKind kind, String content) {
    Command payload = new Command();
    payload.setTenant(new Tenant(TENANT_ID));
    payload.setExecutor(PayloadService.IOC_VALIDATION_POSIX_EXECUTOR);
    payload.setId(
        PayloadService.iocValidationPayloadId(
            kind, PayloadService.IOC_VALIDATION_POSIX_EXECUTOR, TENANT_ID));
    payload.setContent(content);
    return payload;
  }

  /** A validation whose one IOC has the inject as its approved file-drop test. */
  private static IocValidation validation(String testInjectId) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setTestKind(IocValidationTestKind.FILE_DROP);
    ioc.setPlanFingerprint("a".repeat(64));
    ioc.setInjectIds(new ArrayList<>(List.of(testInjectId)));
    ioc.setInjectPayloads(
        new LinkedHashMap<>(
            Map.of(
                testInjectId,
                PayloadService.iocValidationPayloadId(
                    IocValidationTestKind.FILE_DROP,
                    PayloadService.IOC_VALIDATION_POSIX_EXECUTOR,
                    TENANT_ID))));
    IocValidation validation = new IocValidation();
    validation.setSimulationId(SIMULATION_ID);
    validation.setIocs(new ArrayList<>(List.of(ioc)));
    return validation;
  }
}
