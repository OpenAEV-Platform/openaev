package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.Injection;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutableInjectDTOMapper;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression coverage for the legacy-inject injector-resolution fallback in {@link
 * Executor#execute(ExecutableInject)}: when {@code Inject.injector} is not populated, the injector
 * must be re-resolved through a fresh, explicitly tenant-scoped query rather than through the
 * inject's cached, EAGER {@code @Filter}-guarded association graph (which may have been initialized
 * under the wrong tenant context by a prior cross-tenant batch read, e.g. {@code
 * InjectHelper#getInjectsToRun}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorTest {

  @Mock private InjectStatusRepository injectStatusRepository;
  @Mock private InjectorRepository injectorRepository;
  @Mock private RabbitmqService rabbitmqService;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private ManagerFactory managerFactory;
  @Mock private ExecutionExecutorService executionExecutorService;
  @Mock private InjectStatusService injectStatusService;
  @Mock private InjectService injectService;
  @Mock private ExecutableInjectDTOMapper executableInjectDTOMapper;
  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private InjectExpectationService injectExpectationService;

  @InjectMocks private Executor executor;

  private static final String TENANT_ID = "tenant-001";
  private static final String CONTRACT_ID = "contract-001";
  private static final String INJECT_ID = "inject-001";

  private Inject inject;
  private InjectorContract injectorContract;
  private ExecutableInject executableInject;

  @BeforeEach
  void setUp() throws Exception {
    // executor.mapper is a @Resource field, not constructor-injected: @InjectMocks never touches
    // it, so it must be wired manually to avoid a NullPointerException in executeExternal.
    ObjectMapper mapper = new ObjectMapper();
    ReflectionTestUtils.setField(executor, "mapper", mapper);

    Tenant tenant = new Tenant(TENANT_ID);

    injectorContract = mock(InjectorContract.class);
    when(injectorContract.getId()).thenReturn(CONTRACT_ID);
    when(injectorContract.getNeedsExecutor()).thenReturn(false);

    inject = mock(Inject.class);
    when(inject.getId()).thenReturn(INJECT_ID);
    when(inject.getTenant()).thenReturn(tenant);
    when(inject.getInjectorContract()).thenReturn(Optional.of(injectorContract));

    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);

    executableInject = mock(ExecutableInject.class);
    when(executableInject.getInjection()).thenReturn(injection);
  }

  @Nested
  @DisplayName("execute - legacy inject injector fallback resolution")
  class LegacyInjectorFallback {

    @Test
    @DisplayName(
        "Given inject without a populated injector field, execute should resolve it through a fresh tenant-scoped query")
    void given_injectWithoutInjectorField_should_resolveInjectorViaFreshTenantScopedQuery()
        throws Exception {
      // -------- Arrange --------
      when(inject.getInjector()).thenReturn(null);
      when(inject.getType()).thenReturn("openaev_test_injector");

      Injector injector = new Injector();
      injector.setId("injector-001");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);

      when(injectorRepository.findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID))
          .thenReturn(Optional.of(injector));
      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-001"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      when(injectStatusRepository.findByInjectId(INJECT_ID))
          .thenReturn(Optional.of(mock(InjectStatus.class)));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(injectorRepository)
          .findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID);
      // The stale, filter-guarded fallback must not be used anymore
      verify(injectorRepository, never()).findByTypeAndTenantId(any(), any());
    }

    @Test
    @DisplayName(
        "Given inject without a populated injector field and no linked injector for the tenant, execute should fail with a clear error")
    void given_noLinkedInjectorForTenant_should_throwIllegalStateException() {
      // -------- Arrange --------
      when(inject.getInjector()).thenReturn(null);
      when(inject.getType()).thenReturn("openaev_test_injector");
      when(injectorRepository.findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID))
          .thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThatThrownBy(() -> executor.execute(executableInject))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Injector not found for type");
    }

    @Test
    @DisplayName("Given inject with a populated injector field, execute should use it directly")
    void given_injectWithPopulatedInjectorField_should_useItDirectlyWithoutExtraQuery()
        throws Exception {
      // -------- Arrange --------
      Injector injector = new Injector();
      injector.setId("injector-002");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);
      when(inject.getInjector()).thenReturn(injector);

      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-002"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      when(injectStatusRepository.findByInjectId(INJECT_ID))
          .thenReturn(Optional.of(mock(InjectStatus.class)));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verifyNoInteractions(injectorRepository);
    }
  }
}
