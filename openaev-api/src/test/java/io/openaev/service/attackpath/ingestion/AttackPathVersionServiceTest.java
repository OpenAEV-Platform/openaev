package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The version primitive itself (#6647, spec 002): the counter every projection write bumps and
 * stamps on its rows.
 *
 * <p>Two properties are load-bearing and neither is visible from the writers' tests. First, a bump
 * returns the value it wrote — it is ONE statement with {@code RETURNING}, not an increment
 * followed by a read, because between those two a concurrent writer's increment would hand this
 * writer a version it never stamped. Second, the counter is per (simulation, tenant): the table is
 * not tenant-active, so nothing rewrites these statements, and two tenants that happen to share a
 * simulation id must still hold independent counters.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH")
@DisplayName("attack path: the version counter is atomic and per tenant")
class AttackPathVersionServiceTest extends IntegrationTest {

  private static final String SIM = "SIM-VERSION-PRIMITIVE";

  @Autowired private AttackPathVersionService versionService;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    tenantA = tenantRepository.save(TenantFixture.getTenant("ap-version-a"));
    tenantB = tenantRepository.save(TenantFixture.getTenant("ap-version-b"));
  }

  @Test
  @DisplayName("a bump returns the value it stored, starting at 1")
  void aBumpReturnsTheStoredValue() {
    long first = versionService.bump(SIM, tenantA.getId());
    long second = versionService.bump(SIM, tenantA.getId());

    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(2);
    assertThat(versionService.current(SIM, List.of(tenantA.getId()))).contains(second);
  }

  @Test
  @DisplayName("one tenant's bumps leave another tenant's counter for the same simulation alone")
  void countersAreIndependentPerTenant() {
    versionService.bump(SIM, tenantA.getId());
    versionService.bump(SIM, tenantA.getId());

    assertThat(versionService.bump(SIM, tenantB.getId())).isEqualTo(1);
    assertThat(versionService.current(SIM, List.of(tenantA.getId()))).contains(2L);
    assertThat(versionService.current(SIM, List.of(tenantB.getId()))).contains(1L);
  }

  @Test
  @DisplayName("a tenant with no counter reads absent, which is what the delta answers as a resync")
  void aTenantWithoutACounterReadsAbsent() {
    versionService.bump(SIM, tenantA.getId());

    assertThat(versionService.current(SIM, List.of(tenantB.getId()))).isEmpty();
    // No scope at all is fail-closed, like every inspector-filtered read of the projection.
    assertThat(versionService.current(SIM, List.of())).isEmpty();
  }

  @Test
  @DisplayName("deleting a counter deletes only its tenant's")
  void deletingACounterIsTenantScoped() {
    versionService.bump(SIM, tenantA.getId());
    versionService.bump(SIM, tenantB.getId());

    versionService.deleteBySimulationId(SIM, tenantB.getId());

    assertThat(versionService.current(SIM, List.of(tenantA.getId()))).contains(1L);
    assertThat(versionService.current(SIM, List.of(tenantB.getId()))).isEmpty();
  }
}
