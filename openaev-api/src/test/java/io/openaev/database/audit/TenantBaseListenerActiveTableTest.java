package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Team;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * teams is not v2-active today; this test arms it explicitly to prove the rule catches a real
 * unattributed write on a table the moment its attribution moves to v2, regardless of what the
 * table happens to be at the time this test is written.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=teams")
@Transactional
class TenantBaseListenerActiveTableTest extends IntegrationTest {

  @Autowired private TeamRepository teamRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @WithMockUser
  void given_noTenantContextOnActiveTable_should_throwOnPersist() {
    // Arrange
    TenantContext.clearCurrentTenant();
    Team team = new Team();
    team.setName("unattributed team on an active table");

    // Act & Assert
    assertThatThrownBy(() -> teamRepository.save(team))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("unattributed tenant write: Team");
  }
}
