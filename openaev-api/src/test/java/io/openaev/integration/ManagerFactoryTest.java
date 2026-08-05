package io.openaev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.openaev.database.model.Tenant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerFactory unit tests")
class ManagerFactoryTest {

  private ManagerFactory managerFactory;

  private Tenant createTenant(String id, String name) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setName(name);
    return tenant;
  }

  @BeforeEach
  void setUp() {
    // getManager() invokes createManager() through this self-provider so its REQUIRES_NEW
    // transaction applies in production; here the provider simply returns the instance under test.
    // Lenient because the createDependencyForTenant path populates the cache directly and never
    // hits the provider.
    @SuppressWarnings("unchecked")
    ObjectProvider<ManagerFactory> self = mock(ObjectProvider.class);
    managerFactory = new ManagerFactory(List.of(), List.of(), self);
    lenient().when(self.getObject()).thenReturn(managerFactory);
  }

  @Nested
  @DisplayName("getManager(tenantId) — per-tenant Manager creation")
  class GetManager {

    @Test
    @DisplayName("given_validTenantId_should_returnManagerWithCorrectTenantId")
    void given_validTenantId_should_returnManagerWithCorrectTenantId() {
      // Act
      Manager manager = managerFactory.getManager("tenant-a");

      // Assert
      assertThat(manager).isNotNull();
      assertThat(manager.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("given_sameTenantId_should_returnSameManagerInstance")
    void given_sameTenantId_should_returnSameManagerInstance() {
      // Act — call twice with the same tenant
      Manager first = managerFactory.getManager("tenant-a");
      Manager second = managerFactory.getManager("tenant-a");

      // Assert — same instance, Manager is created only once (computeIfAbsent)
      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("given_differentTenantIds_should_returnDistinctManagers")
    void given_differentTenantIds_should_returnDistinctManagers() {
      // Act
      Manager managerA = managerFactory.getManager("tenant-a");
      Manager managerB = managerFactory.getManager("tenant-b");

      // Assert — distinct Manager instances, each scoped to its tenant
      assertThat(managerA).isNotSameAs(managerB);
      assertThat(managerA.getTenantId()).isEqualTo("tenant-a");
      assertThat(managerB.getTenantId()).isEqualTo("tenant-b");
    }
  }

  @Nested
  @DisplayName("createDependencyForTenant — Manager initialization")
  class CreateDependencyForTenant {

    @Test
    @DisplayName("given_newTenant_should_createManagerForTenant")
    void given_newTenant_should_createManagerForTenant() throws Exception {
      // Arrange
      Tenant tenant = createTenant("new-tenant", "New Tenant");

      // Act
      managerFactory.createDependencyForTenant(tenant);

      // Assert — a Manager now exists for that tenant
      Manager manager = managerFactory.getManager("new-tenant");
      assertThat(manager).isNotNull();
      assertThat(manager.getTenantId()).isEqualTo("new-tenant");
    }

    @Test
    @DisplayName("given_sameTenantCalledTwice_should_createManagerOnlyOnce")
    void given_sameTenantCalledTwice_should_createManagerOnlyOnce() throws Exception {
      // Arrange
      Tenant tenant = createTenant("tenant-a", "Tenant A");

      // Act
      managerFactory.createDependencyForTenant(tenant);
      managerFactory.createDependencyForTenant(tenant);

      // Assert — same Manager instance returned on repeated calls (created only once)
      Manager first = managerFactory.getManager("tenant-a");
      Manager second = managerFactory.getManager("tenant-a");
      assertThat(first).isSameAs(second);
    }
  }
}
