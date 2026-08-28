package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the causal seed's step-template &lt;-&gt; condition linkage, the one silent-failure risk:
 * a seeded execution whose {@code step_template_id} is a conditioned step's id must make {@code
 * buildGraph} resolve that step's consumed key ({@code port EQ 445}) onto the execution feed node.
 * If the linkage is wrong the graph builds with an empty {@code consumedFindingKeys} and NO error,
 * so this is pinned before any breadth.
 */
@Transactional
@DisplayName("causal seed: an execution's step_template_id resolves the step's consumed keys")
class AttackPathCausalSeedLinkageTest extends IntegrationTest {

  private static final String SIM = "ap-seed-causal-linkage";

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("buildGraph surfaces (port, EQ, 445) on the execution that ran the conditioned step")
  void execution_step_template_id_resolves_the_step_conditions() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-linkage-tenant"));

    causalSeedService.seedCausalMinimal(SIM, tenant.getId());

    AttackPathDTO dto = graphService.buildGraph(SIM);

    assertThat(dto.attackPathExecutions())
        .as("the execution feed node carries the step's resolved consumed key")
        .anySatisfy(
            node ->
                assertThat(node.getConsumedFindingKeys())
                    .contains(new ConsumedFindingKeyDTO("port", "EQ", "445", null)));
  }
}
