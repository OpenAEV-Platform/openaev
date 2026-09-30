package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.fixtures.CollectorTypeFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

// collector_types is armed active here so the CollectorType cases below keep exercising the
// throw path; teams is left out on purpose so WhenTheTableIsNotV2Active exercises the fallback.
@TestPropertySource(properties = "openaev.tenant.active-tables=collector_types")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class TenantBaseListenerTest extends IntegrationTest {

  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private TeamRepository teamRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  class WhenNoTenantIsAmbient {

    @Test
    @WithMockUser
    void given_noTenantContext_should_throwOnPersist() {
      // Arrange
      TenantContext.clearCurrentTenant();
      CollectorType collectorType = CollectorTypeFixture.createDefaultCollectorType();

      // Act & Assert
      assertThatThrownBy(() -> collectorTypeRepository.save(collectorType))
          .hasRootCauseInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("unattributed tenant write: CollectorType");
    }
  }

  @Nested
  class WhenATenantIsAmbient {

    @Test
    @WithMockUser
    void given_tenantContextSet_should_stampTenantOnPersist() {
      // Arrange
      TenantContext.setCurrentTenant(Tenant.DEFAULT_TENANT_UUID);
      CollectorType collectorType =
          CollectorTypeFixture.createCollectorType("test_collector_type_stamped");

      // Act
      CollectorType saved = collectorTypeRepository.save(collectorType);

      // Assert
      assertThat(saved.getTenant()).isNotNull();
      assertThat(saved.getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }

  @Nested
  class WhenTheTableIsNotV2Active {

    @Test
    @WithMockUser
    void given_noTenantContextOnInactiveTable_should_stampDefaultTenant() {
      // Arrange
      // teams is not in openaev.tenant.active-tables: the legacy contract still owns its
      // attribution, so an unattributed write must keep falling back to the default tenant
      // instead of being refused.
      TenantContext.clearCurrentTenant();
      Team team = new Team();
      team.setName("unattributed team");

      // Act
      Team saved = teamRepository.save(team);

      // Assert
      assertThat(saved.getTenant()).isNotNull();
      assertThat(saved.getTenant().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
