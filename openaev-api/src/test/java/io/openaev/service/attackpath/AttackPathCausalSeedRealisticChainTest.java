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
 * The realistic chain must be a connected multi-hop path: several pivot hops each consume the
 * finding the previous hop produced (port then share then credentials), so buildGraph resolves a
 * matched producer on every consumer, not just the first edge.
 */
@Transactional
@DisplayName("causal seed: the realistic chain connects multiple hops end to end")
class AttackPathCausalSeedRealisticChainTest extends IntegrationTest {

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("every pivot hop resolves the prior hop's finding (>= 3 causal edges)")
  void realistic_chain_connects_multiple_hops() {
    Tenant tenant =
        tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-realistic-tenant"));

    String sim = causalSeedService.seedRealisticChain(tenant.getId());

    AttackPathDTO dto = graphService.buildGraph(sim);

    long causalHops =
        dto.attackPathExecutions().stream()
            .filter(
                node ->
                    node.getConsumedFindingKeys() != null
                        && node.getConsumedFindingKeys().stream()
                            .anyMatch(key -> !key.matchedFindingIds().isEmpty()))
            .count();

    assertThat(causalHops)
        .as("a multi-hop chain: each pivot consumes the finding of the hop before it")
        .isGreaterThanOrEqualTo(3);
  }
}
