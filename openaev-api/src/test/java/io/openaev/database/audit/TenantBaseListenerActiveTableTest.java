package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * injects is not v2-active today; this test arms it explicitly to prove the rule catches a real
 * unattributed write on a table the moment its attribution moves to v2, regardless of what the
 * table happens to be at the time this test is written. It must keep naming a table that is still
 * inactive: on an active table the listener is removed from the entity altogether, so the rule
 * would have nothing to fire on.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=injects")
@Transactional
class TenantBaseListenerActiveTableTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @WithMockUser
  void given_noTenantContextOnActiveTable_should_throwOnPersist() {
    // Arrange
    TenantContext.clearCurrentTenant();
    Inject inject = new Inject();
    inject.setTitle("unattributed inject on an active table");
    inject.setDependsDuration(0L);

    // Act & Assert
    assertThatThrownBy(() -> injectRepository.save(inject))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("unattributed tenant write: Inject");
  }
}
