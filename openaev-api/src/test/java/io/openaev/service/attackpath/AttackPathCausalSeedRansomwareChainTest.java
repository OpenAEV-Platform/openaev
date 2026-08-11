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
 * The ransomware chain is a deep AND wide kill chain: a multi-hop initial-access spine (phishing,
 * discovery, SMB exploit, credential dump) lands a reused local admin, which fans out across a
 * workstation tier, converges on the domain controller through a share/Kerberoast/DCSync
 * escalation, and finally fans out again as domain-wide impact. Every hop consumes the prior hop's
 * finding, so buildGraph resolves a matched producer on all of them: the spine, both fan-outs and
 * the convergence.
 */
@Transactional
@DisplayName("causal seed: the ransomware chain is deep and wide, converging on the DC")
class AttackPathCausalSeedRansomwareChainTest extends IntegrationTest {

  private static final int SPREAD = 5;

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("every spine, fan-out and convergence hop resolves its producer")
  void ransomware_chain_is_deep_and_wide() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-seed-ransomware-tenant"));

    String sim = causalSeedService.seedRansomwareChain(tenant.getId(), SPREAD);

    AttackPathDTO dto = graphService.buildGraph(sim);

    long causalHops =
        dto.attackPathExecutions().stream()
            .filter(
                node ->
                    node.getConsumedFindingKeys() != null
                        && node.getConsumedFindingKeys().stream()
                            .anyMatch(key -> !key.matchedFindingIds().isEmpty()))
            .count();

    // Initial-access spine (discovery, exploit, dump) + workstation spray (SPREAD) + privileged
    // spine (share, Kerberoast, DCSync) + workstation-to-DC convergence (SPREAD) + domain-wide
    // impact (SPREAD). If any deep hop failed to resolve its producer, the count drops below this.
    assertThat(causalHops)
        .as("the whole kill chain resolves: spine, both fan-outs and the convergence")
        .isGreaterThanOrEqualTo(3L + SPREAD + 3L + SPREAD + SPREAD);
  }
}
