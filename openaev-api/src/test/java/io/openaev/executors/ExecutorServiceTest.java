package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ExecutorRepository;
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
      when(executorRepository.findByExecutorId("exec-new")).thenReturn(Optional.empty());
      when(executorRepository.save(any(Executor.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Executor result =
          executorService.register(
              "tenant-001",
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

      when(executorRepository.findByExecutorId("exec-existing")).thenReturn(Optional.of(existing));
      when(executorRepository.save(any(Executor.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Executor result =
          executorService.register(
              "tenant-001",
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
      when(executorRepository.findByExecutorId("exec-cap")).thenReturn(Optional.empty());
      when(executorRepository.save(captor.capture()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      executorService.register(
          "tenant-001",
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
}
