package io.openaev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManagerException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerFactory unit tests")
class ManagerFactoryTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantRegistrationExecutor tenantRegistrationExecutor;

  private ManagerFactory managerFactory;

  private Tenant createTenant(String id, String name) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setName(name);
    return tenant;
  }

  @BeforeEach
  void setUp() {
    // Use an empty factory list so Manager constructor succeeds without side effects.
    managerFactory = new ManagerFactory(List.of(), tenantRepository, tenantRegistrationExecutor);
  }

  @Nested
  @DisplayName("getManager(tenantId) — per-tenant Manager creation")
  class GetManager {

    @Test
    @DisplayName("given_validTenantId_should_registerBuiltinsAndReturnManager")
    void given_validTenantId_should_registerBuiltinsAndReturnManager() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      when(tenantRepository.findById("tenant-a")).thenReturn(Optional.of(tenantA));

      // Act
      Manager manager = managerFactory.getManager("tenant-a");

      // Assert
      assertThat(manager).isNotNull();
      assertThat(manager.getTenantId()).isEqualTo("tenant-a");
      verify(tenantRegistrationExecutor).registerForTenantIsolated(tenantA);
    }

    @Test
    @DisplayName("given_sameTenantId_should_returnSameManagerInstance")
    void given_sameTenantId_should_returnSameManagerInstance() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      when(tenantRepository.findById("tenant-a")).thenReturn(Optional.of(tenantA));

      // Act — call twice with the same tenant
      Manager first = managerFactory.getManager("tenant-a");
      Manager second = managerFactory.getManager("tenant-a");

      // Assert — same instance returned, registration only runs once
      assertThat(first).isSameAs(second);
      verify(tenantRegistrationExecutor, times(1)).registerForTenantIsolated(tenantA);
    }

    @Test
    @DisplayName("given_differentTenantIds_should_returnDistinctManagers")
    void given_differentTenantIds_should_returnDistinctManagers() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      Tenant tenantB = createTenant("tenant-b", "Tenant B");
      when(tenantRepository.findById("tenant-a")).thenReturn(Optional.of(tenantA));
      when(tenantRepository.findById("tenant-b")).thenReturn(Optional.of(tenantB));

      // Act
      Manager managerA = managerFactory.getManager("tenant-a");
      Manager managerB = managerFactory.getManager("tenant-b");

      // Assert — distinct Manager instances, each with its own tenantId
      assertThat(managerA).isNotSameAs(managerB);
      assertThat(managerA.getTenantId()).isEqualTo("tenant-a");
      assertThat(managerB.getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("given_unknownTenantId_should_throwException")
    void given_unknownTenantId_should_throwException() {
      // Arrange
      when(tenantRepository.findById("unknown")).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> managerFactory.getManager("unknown"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to initialize Manager for tenant unknown");
    }

    @Test
    @DisplayName("given_failingRegistration_should_throwWrappedException")
    void given_failingRegistration_should_throwWrappedException() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      when(tenantRepository.findById("tenant-a")).thenReturn(Optional.of(tenantA));
      doThrow(new DependenciesManagerException("boom", new RuntimeException()))
          .when(tenantRegistrationExecutor)
          .registerForTenantIsolated(tenantA);

      // Act & Assert — registration error is logged but not surfaced as RuntimeException
      assertThatNoException().isThrownBy(() -> managerFactory.getManager("tenant-a"));
    }
  }

  @Nested
  @DisplayName("Tenant creation path (createDependencyForTenant)")
  class TenantCreationPath {

    @Test
    @DisplayName("given_newTenant_should_callRegisterForTenant")
    void given_newTenant_should_callRegisterForTenant() throws Exception {
      // Arrange
      Tenant tenant = createTenant("new-tenant", "New Tenant");

      // Act
      managerFactory.createDependencyForTenant(tenant);

      // Assert — uses join-transaction variant, not isolated
      verify(tenantRegistrationExecutor).registerForTenant(tenant);
      verify(tenantRegistrationExecutor, never()).registerForTenantIsolated(any());
    }
  }
}
