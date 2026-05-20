package io.openaev.integration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManagerException;
import java.util.List;
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
  @DisplayName("Startup path (registerBuiltinsForAllTenants)")
  class StartupPath {

    @Test
    @DisplayName("given_multipleTenants_should_callRegisterForTenantIsolatedForEach")
    void given_multipleTenants_should_callRegisterForTenantIsolatedForEach() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      Tenant tenantB = createTenant("tenant-b", "Tenant B");
      when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));

      // Act — getManager() triggers registerBuiltinsForAllTenants()
      managerFactory.getManager();

      // Assert — each tenant gets its own isolated registration
      verify(tenantRegistrationExecutor).registerForTenantIsolated(tenantA);
      verify(tenantRegistrationExecutor).registerForTenantIsolated(tenantB);
      // registerForTenant (join-transaction) should NOT be called in startup path
      verify(tenantRegistrationExecutor, never()).registerForTenant(any());
    }

    @Test
    @DisplayName("given_failingTenant_should_continueWithRemainingTenants")
    void given_failingTenant_should_continueWithRemainingTenants() throws Exception {
      // Arrange
      Tenant tenantA = createTenant("tenant-a", "Tenant A");
      Tenant tenantB = createTenant("tenant-b", "Tenant B");
      when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
      doThrow(new DependenciesManagerException("boom", new RuntimeException()))
          .when(tenantRegistrationExecutor)
          .registerForTenantIsolated(tenantA);

      // Act — should not throw, errors are logged per tenant
      assertThatNoException().isThrownBy(() -> managerFactory.getManager());

      // Assert — tenant B still gets registered despite tenant A failure
      verify(tenantRegistrationExecutor).registerForTenantIsolated(tenantB);
    }

    @Test
    @DisplayName("given_noTenants_should_succeedWithoutRegistration")
    void given_noTenants_should_succeedWithoutRegistration() {
      // Arrange
      when(tenantRepository.findAll()).thenReturn(List.of());

      // Act
      assertThatNoException().isThrownBy(() -> managerFactory.getManager());

      // Assert
      verifyNoInteractions(tenantRegistrationExecutor);
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
