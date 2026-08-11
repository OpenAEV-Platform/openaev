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
 * The scaled seed must fan out into many independent paths that converge on the DC: each foothold
 * consumes its OWN credential (no cross-match between paths), so buildGraph resolves a matched
 * producer on every foothold's lateral hop, one per foothold.
 */
@Transactional
@DisplayName("causal seed: the scaled chain fans out into converging paths")
class AttackPathCausalSeedScaledChainTest extends IntegrationTest {

  private static final int FOOTHOLDS = 8;

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("each foothold resolves its own credential into a lateral hop (>= N causal paths)")
  void scaled_chain_fans_out_into_converging_paths() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-scaled-tenant"));

    String sim = causalSeedService.seedScaledChain(tenant.getId(), FOOTHOLDS);

    AttackPathDTO dto = graphService.buildGraph(sim);

    long convergingPaths =
        dto.attackPathExecutions().stream()
            .filter(
                node ->
                    node.getConsumedFindingKeys() != null
                        && node.getConsumedFindingKeys().stream()
                            .anyMatch(key -> !key.matchedFindingIds().isEmpty()))
            .count();

    assertThat(convergingPaths)
        .as("one converging causal path per foothold, each on its own credential")
        .isGreaterThanOrEqualTo(FOOTHOLDS);
  }
}
