package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The deep intrusion is a long, mostly linear kill chain: the attacker pivots host to host across
 * the estate to the domain controller, each hop consuming the finding the previous host yielded, so
 * buildGraph resolves a matched producer on every hop of the chain (not just the first).
 */
@Transactional
@DisplayName("causal seed: the deep intrusion pivots host to host to the DC")
class AttackPathCausalSeedDeepIntrusionTest extends IntegrationTest {

  private static final int HOPS = 12;

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("every host-to-host hop resolves the prior host's finding")
  void deep_intrusion_pivots_host_to_host() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-intrusion-tenant"));

    String sim = causalSeedService.seedDeepIntrusion(tenant.getId(), HOPS);

    AttackPathDTO dto = graphService.buildGraph(sim);

    long causalHops =
        dto.attackPathExecutions().stream()
            .filter(
                node ->
                    node.getConsumedFindingKeys() != null
                        && node.getConsumedFindingKeys().stream()
                            .anyMatch(key -> !key.matchedFindingIds().isEmpty()))
            .count();

    // recon, exploit and the file-server foothold, then HOPS lateral pivots, then DCSync, plus the
    // three-way ransomware impact.
    assertThat(causalHops)
        .as("a long host-to-host chain: each pivot consumes the finding of the host before it")
        .isGreaterThanOrEqualTo(HOPS + 7);
  }
}
