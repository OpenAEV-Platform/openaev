package io.openaev.service.stix;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.database.repository.IocValidationRepository.IocValidationRef;
import io.openaev.opencti.client.mutations.IocValidationRequestStatusUpdate;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.scenario.ScenarioService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * The write-back jobs walk their outbox, which spans every tenant, a bounded page per run: the
 * OpenCTI of a tenant unreachable skips the other validations of that tenant in the page, and a
 * validation OpenCTI refuses is skipped, so that neither holds the others back.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IOC validation outbox walk")
class IocValidationOutboxWalkTest {

  private static final String TENANT = "tenant-1";
  private static final String OTHER_TENANT = "tenant-2";

  @Mock private IocValidationRepository iocValidationRepository;
  @Mock private IocValidationBundleParser bundleParser;
  @Mock private IocValidationSettingsService settingsService;
  @Mock private AssetService assetService;
  @Mock private AssetGroupService assetGroupService;
  @Mock private PayloadService payloadService;
  @Mock private InjectorContractService injectorContractService;
  @Mock private InjectService injectService;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private ScenarioService scenarioService;
  @Mock private ScenarioToExerciseService scenarioToExerciseService;
  @Mock private ExerciseService exerciseService;
  @Mock private TagService tagService;
  @Mock private OpenCTIConnectorService openCTIConnectorService;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private IocValidationService service;

  private record Ref(String getId, String getTenantId) implements IocValidationRef {}

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    lenient()
        .when(tenantTx.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    lenient()
        .doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
    lenient().when(openAEVConfig.getBaseUrl()).thenReturn("http://localhost:8080");
    registerConnector(TENANT);
    lenient()
        .when(iocValidationRepository.findById(anyString()))
        .thenAnswer(invocation -> Optional.of(awaitingApproval(invocation.getArgument(0))));
  }

  @Test
  @DisplayName(
      "given the OpenCTI of one tenant unreachable should skip that tenant only and report the others")
  void given_openCtiOfOneTenantUnreachable_should_skipThatTenantOnly() throws Exception {
    registerConnector(OTHER_TENANT);
    when(iocValidationRepository.findRefsWithPendingLifecycleSync(anyString(), any()))
        .thenReturn(
            List.of(
                new Ref("a", TENANT),
                new Ref("b", TENANT),
                new Ref("c", TENANT),
                new Ref("d", OTHER_TENANT)));
    lenient()
        .doThrow(new IOException("connection refused"))
        .when(openCTIConnectorService)
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "b")), eq(TENANT));

    service.syncPendingLifecycles();

    verify(openCTIConnectorService)
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "a")), eq(TENANT));
    // One failed call for the unreachable tenant, whose next validation waits for a later walk
    verify(openCTIConnectorService, never())
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "c")), eq(TENANT));
    verify(openCTIConnectorService)
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "d")), eq(OTHER_TENANT));

    // The shared cursor is not pinned on the failed validation: the walk goes on
    service.syncPendingLifecycles();

    verify(iocValidationRepository, times(2))
        .findRefsWithPendingLifecycleSync(
            "", PageRequest.of(0, IocValidationService.OUTBOX_PAGE_SIZE));
    verify(iocValidationRepository, never()).findRefsWithPendingLifecycleSync(eq("b"), any());
  }

  @Test
  @DisplayName(
      "given the OpenCTI of one tenant unreachable should still push the results of the others")
  void given_openCtiOfOneTenantUnreachable_should_pushTheResultsOfTheOthers() throws Exception {
    registerConnector(OTHER_TENANT);
    when(iocValidationRepository.findRefsWithPendingResultsPush(anyString(), any()))
        .thenReturn(
            List.of(new Ref("a", TENANT), new Ref("b", TENANT), new Ref("c", OTHER_TENANT)));
    doThrow(new IOException("connection refused"))
        .when(openCTIConnectorService)
        .pushIocValidationStixBundle(any(), eq(TENANT));

    service.pushPendingResults();

    verify(openCTIConnectorService).pushIocValidationStixBundle(any(), eq(TENANT));
    verify(openCTIConnectorService).pushIocValidationStixBundle(any(), eq(OTHER_TENANT));

    service.pushPendingResults();

    verify(iocValidationRepository, times(2))
        .findRefsWithPendingResultsPush(
            "", PageRequest.of(0, IocValidationService.OUTBOX_PAGE_SIZE));
    verify(iocValidationRepository, never()).findRefsWithPendingResultsPush(eq("a"), any());
  }

  @Test
  @DisplayName("given a validation OpenCTI refuses should skip it and report the next ones")
  void given_validationRefused_should_skipItAndReportTheNextOnes() throws Exception {
    when(iocValidationRepository.findRefsWithPendingLifecycleSync(anyString(), any()))
        .thenReturn(List.of(new Ref("a", TENANT), new Ref("b", TENANT)));
    lenient()
        .doThrow(new ConnectorError("refused"))
        .when(openCTIConnectorService)
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "a")), eq(TENANT));

    service.syncPendingLifecycles();

    verify(openCTIConnectorService)
        .updateIocValidationRequestStatus(argThat(update -> isFor(update, "b")), eq(TENANT));
  }

  @Test
  @DisplayName("given a full page should continue from its last validation on the next run")
  void given_fullPage_should_continueFromItsLastValidation() {
    List<IocValidationRef> fullPage =
        IntStream.range(0, IocValidationService.OUTBOX_PAGE_SIZE)
            .mapToObj(i -> (IocValidationRef) new Ref("id-%03d".formatted(i), TENANT))
            .toList();
    when(iocValidationRepository.findRefsWithPendingResultsPush(anyString(), any()))
        .thenReturn(fullPage, List.of());
    when(iocValidationRepository.findById(anyString())).thenReturn(Optional.empty());

    service.pushPendingResults();
    service.pushPendingResults();
    service.pushPendingResults();

    PageRequest page = PageRequest.of(0, IocValidationService.OUTBOX_PAGE_SIZE);
    verify(iocValidationRepository, times(2)).findRefsWithPendingResultsPush("", page);
    verify(iocValidationRepository)
        .findRefsWithPendingResultsPush(fullPage.getLast().getId(), page);
  }

  @Test
  @DisplayName("given many running validations should evaluate one bounded page per run")
  void given_manyRunningValidations_should_evaluateOneBoundedPagePerRun() {
    List<IocValidationRef> fullPage =
        IntStream.range(0, IocValidationService.OUTBOX_PAGE_SIZE)
            .mapToObj(i -> (IocValidationRef) new Ref("id-%03d".formatted(i), TENANT))
            .toList();
    when(iocValidationRepository.findRunningRefs(anyString(), any()))
        .thenReturn(fullPage, List.of());

    service.computeRunningResults();
    service.computeRunningResults();

    PageRequest page = PageRequest.of(0, IocValidationService.OUTBOX_PAGE_SIZE);
    verify(iocValidationRepository).findRunningRefs("", page);
    verify(iocValidationRepository).findRunningRefs(fullPage.getLast().getId(), page);
  }

  private void registerConnector(String tenantId) {
    ConnectorBase connector = mock(ConnectorBase.class);
    lenient().when(connector.isRegistered()).thenReturn(true);
    lenient()
        .when(openCTIConnectorService.getIocValidationConnector(tenantId))
        .thenReturn(Optional.of(connector));
  }

  private static boolean isFor(IocValidationRequestStatusUpdate update, String id) {
    return update != null && ("ioc-validation-request--" + id).equals(update.getRequestId());
  }

  private static IocValidation awaitingApproval(String id) {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT);
    IocValidation validation = new IocValidation();
    validation.setId(id);
    validation.setExternalId("ioc-validation-request--" + id);
    validation.setStatus(IocValidationStatus.AWAITING_APPROVAL);
    validation.setTenant(tenant);
    return validation;
  }
}
