package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A seeded execution must be byte-faithful to what the real engine writes: the deterministic {@code
 * NODE_EXECUTION|...} id, the frozen identity keys ({@code injector_type}, {@code
 * step_template_id}), the source/target shape (INJECTOR + ASSET, {@code target_asset_id ==
 * target_key}) and {@code payload_name = inject title}. Built through the engine's OWN setters so
 * the columns are computed by production code, not re-derived here.
 */
@Transactional
@DisplayName("causal seed: a seeded execution matches the real engine's column shape")
class AttackPathCausalSeedFidelityTest extends IntegrationTest {

  private static final String SIM = "ap-seed-causal-fidelity";

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathExecutionRepository executionRepository;

  @Test
  @DisplayName("the seeded execution carries the engine's deterministic id and frozen columns")
  void seeded_execution_matches_the_engine_column_shape() {
    Tenant tenant =
        tenantRepository.save(TenantFixture.getTenant("ap-seed-causal-fidelity-tenant"));

    causalSeedService.seedFaithfulExecution(SIM, tenant.getId());

    List<AttackPathExecution> rows =
        StreamSupport.stream(executionRepository.findAll().spliterator(), false)
            .filter(e -> SIM.equals(e.getSimulationId()))
            .toList();
    assertThat(rows).hasSize(1);
    AttackPathExecution e = rows.get(0);

    assertThat(e.getId()).as("deterministic engine id").startsWith("NODE_EXECUTION|");
    assertThat(e.getStepTemplateId())
        .as("frozen step template id (kill-chain linkage)")
        .isNotNull();
    assertThat(e.getInjectorType()).as("frozen injector type").isNotBlank();
    assertThat(e.getSourceKind()).isEqualTo("INJECTOR");
    assertThat(e.getSourceInjector()).as("injector name").isNotBlank();
    assertThat(e.getTargetKind()).isEqualTo("ASSET");
    assertThat(e.getTargetAssetId())
        .as("ASSET target: asset id == target key")
        .isEqualTo(e.getTargetKey());
    assertThat(e.getPayloadName()).as("payload_name = inject title").isNotBlank();
    assertThat(e.getExecutedAt()).isNotNull();
    assertThat(e.getRowVersion()).isNotNull();
  }
}
