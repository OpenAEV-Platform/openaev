package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The delta's resync bound (#6647, spec 002, FR17): past {@code openaev.attackpath.delta-max-rows}
 * changed rows, a cursor is answered with a resync instead of a snapshot-sized delta. Its own test
 * class because the bound has to be lowered to be reachable — the shipped default is 5000 rows, and
 * writing them to observe one boolean would be a slow test of nothing.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=ATTACK_PATH",
      "openaev.attackpath.delta-max-rows=2"
    })
@DisplayName("attack path: a far-behind cursor is answered with a resync, not a huge delta")
class AttackPathDeltaThresholdTest extends IntegrationTest {

  private static final String SIM = "SIM-DELTA-BOUND";

  @Autowired private AttackPathDeltaService deltaService;
  @Autowired private AttackPathVersionService versionService;
  @Autowired private AttackPathExecutionRepository executionRepository;

  private Tenant tenant;
  private long pendingVersion;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-delta-bound-tenant"));
  }

  @Test
  @DisplayName("at the bound the delta is still assembled")
  void atTheBoundTheDeltaIsAssembled() {
    long v1 = write("dc-01");
    write("dc-02");
    write("dc-03");

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, v1, Set.of(tenant.getId()));

    assertThat(delta.resyncRequired()).isFalse();
    assertThat(delta.attackPathNodes()).isNotEmpty();
  }

  @Test
  @DisplayName("beyond the bound the client is told to re-read the snapshot, with no rows shipped")
  void beyondTheBoundTheClientResyncs() {
    long v1 = write("dc-01");
    write("dc-02");
    write("dc-03");
    write("dc-04");

    AttackPathDeltaDTO delta = deltaService.buildDelta(SIM, v1, Set.of(tenant.getId()));

    assertThat(delta.resyncRequired()).isTrue();
    assertThat(delta.newVersion()).isGreaterThan(v1);
    assertThat(delta.attackPathNodes()).isEmpty();
    assertThat(delta.attackPathEdges()).isEmpty();
    assertThat(delta.counters()).isNull();
  }

  /** One writer's transaction: take the version, stamp it on the row it writes. */
  private long write(String targetKey) {
    pendingVersion = versionService.bump(SIM, tenant.getId());
    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(SIM);
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetAssetId(targetKey);
    execution.setTargetKey(targetKey);
    execution.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    execution.setRowVersion(pendingVersion);
    executionRepository.save(execution);
    entityManager.flush();
    return pendingVersion;
  }
}
