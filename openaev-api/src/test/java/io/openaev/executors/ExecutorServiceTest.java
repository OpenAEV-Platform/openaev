package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.ExecutorMapper;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutorServiceTest {

  @Mock private ExecutorRepository executorRepository;
  @Mock private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  @Mock private ExecutionTraceRepository executionTraceRepository;
  @Mock private FileService fileService;
  @Mock private CatalogConnectorService catalogConnectorService;
  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private ExecutorMapper executorMapper;
  @Mock private CatalogConnectorMapper catalogConnectorMapper;
  @Mock private EndpointService endpointService;
  @InjectMocks private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenant("tenant-001");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("register - Composite join fix (executor_id + tenant_id)")
  class Register {

    @Test
    @DisplayName(
        "Given new executor, register should set tenantId so @JoinColumnsOrFormulas resolves correctly")
    void given_newExecutor_should_setTenantIdForCompositeJoinResolution() throws Exception {
      // -------- Arrange --------
      when(executorRepository.findByIdAndTenantId("exec-new", "tenant-001"))
          .thenReturn(Optional.empty());
      when(executorRepository.save(any(Executor.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Executor result =
          executorService.register(
              "exec-new",
              "openaev_paloaltocortex_executor",
              "PaloAltoCortex",
              "https://docs.example.com",
              "#00CC66",
              null,
              null,
              new String[] {"Linux", "Windows"});

      // -------- Assert --------
      assertThat(result.getTenantId())
          .as("Executor tenant ID should match current tenant")
          .isEqualTo("tenant-001");
      assertThat(result.getTenantId())
          .as(
              "Executor.tenantId (read-only field) must be set explicitly for "
                  + "@JoinColumnsOrFormulas composite join resolution when persisting Agent")
          .isNotNull()
          .isEqualTo("tenant-001");
    }

    @Test
    @DisplayName(
        "Given existing executor, register should not overwrite tenant and should update fields")
    void given_existingExecutor_should_updateFieldsWithoutChangingTenant() throws Exception {
      // -------- Arrange --------
      Executor existing = new Executor();
      existing.setId("exec-existing");
      existing.setName("OldName");
      existing.setType("openaev_crowdstrike_executor");
      existing.setTenantId("tenant-001");

      when(executorRepository.findByIdAndTenantId("exec-existing", "tenant-001"))
          .thenReturn(Optional.of(existing));
      when(executorRepository.save(any(Executor.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Executor result =
          executorService.register(
              "exec-existing",
              "openaev_crowdstrike_executor",
              "NewName",
              "https://docs.example.com",
              "#E12E37",
              null,
              null,
              new String[] {"Windows"});

      // -------- Assert --------
      assertThat(result.getName()).isEqualTo("NewName");
      assertThat(result.getTenantId())
          .as("Existing executor tenantId should remain unchanged")
          .isEqualTo("tenant-001");
    }

    @Test
    @DisplayName("Given new executor, saved entity should have both tenant and tenantId consistent")
    void given_newExecutor_savedEntity_should_haveTenantAndTenantIdConsistent() throws Exception {
      // -------- Arrange --------
      ArgumentCaptor<Executor> captor = ArgumentCaptor.forClass(Executor.class);
      when(executorRepository.findByIdAndTenantId("exec-cap", "tenant-001"))
          .thenReturn(Optional.empty());
      when(executorRepository.save(captor.capture()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      executorService.register(
          "exec-cap",
          "openaev_test_executor",
          "TestExecutor",
          null,
          null,
          null,
          null,
          new String[] {"Linux"});

      // -------- Assert --------
      Executor saved = captor.getValue();
      assertThat(saved.getTenantId())
          .as("tenantId must be set correctly")
          .isNotNull()
          .isEqualTo("tenant-001");
    }
  }

  @Nested
  @DisplayName("remove - tenant-scoped teardown of the owning connector instance")
  class Remove {

    private Executor executorInCurrentTenant(String id) {
      Executor executor = new Executor();
      executor.setId(id);
      executor.setTenantId("tenant-001");
      return executor;
    }

    @Test
    @DisplayName("Given a connector id not visible in the current tenant, nothing is deleted")
    void given_foreignConnectorId_should_notResolveNorDeleteAnyInstance() throws Exception {
      // The id belongs to another tenant: the tenant-scoped lookup sees nothing
      when(executorRepository.findByIdAndTenantId("exec-foreign", "tenant-001"))
          .thenReturn(Optional.empty());

      executorService.remove("exec-foreign");

      // The owning-instance resolution must never run for a foreign id - resolving it through
      // the unscoped configuration table is exactly the cross-tenant delete being prevented
      verifyNoInteractions(connectorInstanceConfigurationRepository);
      verifyNoInteractions(connectorInstanceService);
      verify(executorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Given an owned executor with an owning instance, the instance delete wins")
    void given_ownedExecutorWithInstance_should_deleteInstanceOnly() throws Exception {
      when(executorRepository.findByIdAndTenantId("exec-owned", "tenant-001"))
          .thenReturn(Optional.of(executorInCurrentTenant("exec-owned")));
      ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase ids =
          mock(ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase.class);
      when(ids.getConnectorInstanceId()).thenReturn("instance-1");
      when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
              "EXECUTOR_ID", "exec-owned", "tenant-001"))
          .thenReturn(ids);

      executorService.remove("exec-owned");

      verify(connectorInstanceService).deleteById("instance-1");
      // The instance delete cascades to the executor row: no direct row delete
      verify(executorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Given an owned executor without owning instance, the row delete runs")
    void given_ownedExecutorWithoutInstance_should_fallBackToRowDelete() throws Exception {
      Executor executor = executorInCurrentTenant("exec-manual");
      when(executorRepository.findByIdAndTenantId("exec-manual", "tenant-001"))
          .thenReturn(Optional.of(executor));
      when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
              "EXECUTOR_ID", "exec-manual", "tenant-001"))
          .thenReturn(null);

      executorService.remove("exec-manual");

      verify(connectorInstanceService, never()).deleteById(any());
      verify(endpointService).removeSourceTagsForExecutor("exec-manual", "tenant-001");
      verify(executorRepository).delete(executor);
    }
  }
}
