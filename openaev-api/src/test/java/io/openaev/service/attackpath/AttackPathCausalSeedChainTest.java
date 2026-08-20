package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The minimal causal chain must render as a connected source-to-destination path: a recon execution
 * produces a portscan finding, and the exploit execution that consumes {@code port EQ 445} resolves
 * that finding as its matched producer, so buildGraph carries a non-empty {@code matchedFindingIds}
 * (the causal edge) on the consumer.
 */
@Transactional
@DisplayName("causal seed: the minimal chain renders a connected causal edge")
class AttackPathCausalSeedChainTest extends IntegrationTest {

  private static final String SIM = "ap-seed-causal-chain";

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathVersionService versionService;

  @Test
  @DisplayName("the exploit's port key resolves the recon's portscan finding as its producer")
  void chain_renders_a_causal_edge() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-chain-tenant"));

    causalSeedService.seedMinimalCausalChain(SIM, tenant.getId());

    AttackPathDTO dto = graphService.buildGraph(SIM);

    assertThat(dto.attackPathExecutions())
        .as("a consumer execution's consumed key resolves to a produced finding (causal edge)")
        .anySatisfy(
            node ->
                assertThat(node.getConsumedFindingKeys())
                    .anySatisfy(key -> assertThat(key.matchedFindingIds()).isNotEmpty()));
  }

  @Test
  @DisplayName("the chain writes a graph version and stamps every execution at or below it")
  void chain_stamps_a_coherent_version() {
    Tenant tenant =
        tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-chain-version-tenant"));

    causalSeedService.seedMinimalCausalChain(SIM, tenant.getId());

    long version = versionService.current(SIM, List.of(tenant.getId())).orElse(0L);
    assertThat(version).as("a graph version was bumped for the simulation").isPositive();
    assertThat(
            StreamSupport.stream(executionRepository.findAll().spliterator(), false)
                .filter(e -> SIM.equals(e.getSimulationId()))
                .toList())
        .as("every seeded execution is stamped at or below the current graph version")
        .isNotEmpty()
        .allSatisfy(e -> assertThat(e.getRowVersion()).isLessThanOrEqualTo(version));
  }
}
