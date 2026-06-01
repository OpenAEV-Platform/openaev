package io.openaev.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerFactory unit tests")
class ManagerFactoryTest {

  private ManagerFactory managerFactory;

  @BeforeEach
  void setUp() {
    managerFactory = new ManagerFactory(List.of(), List.of());
  }

  @Nested
  @DisplayName("getManager(tenantId)")
  class GetManager {

    @Test
    @DisplayName("given_tenantId_should_createManagerAndReturnIt")
    void given_tenantId_should_createManagerAndReturnIt() {
      // Act
      assertThatNoException().isThrownBy(() -> managerFactory.getManager("tenant-a"));
    }

    @Test
    @DisplayName("given_sameTenantId_should_returnSameManagerInstance")
    void given_sameTenantId_should_returnSameManagerInstance() {
      // Act
      Manager first = managerFactory.getManager("tenant-a");
      Manager second = managerFactory.getManager("tenant-a");

      // Assert
      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("given_differentTenantIds_should_returnDifferentManagers")
    void given_differentTenantIds_should_returnDifferentManagers() {
      // Act
      Manager managerA = managerFactory.getManager("tenant-a");
      Manager managerB = managerFactory.getManager("tenant-b");

      // Assert
      assertThat(managerA).isNotSameAs(managerB);
    }
  }

  @Nested
  @DisplayName("deleteDependencyForTenant")
  class DeleteDependency {

    @Test
    @DisplayName("given_existingTenant_should_evictManager")
    void given_existingTenant_should_evictManager() {
      // Arrange
      Manager original = managerFactory.getManager("tenant-a");

      // Act
      managerFactory.deleteDependencyForTenant("tenant-a");
      Manager afterEviction = managerFactory.getManager("tenant-a");

      // Assert — new instance created after eviction
      assertThat(afterEviction).isNotSameAs(original);
    }
  }
}
