package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.dto.AttackPathReplayStepDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live replay lands the ransomware kill chain one stage at a time onto an existing simulation:
 * each call plays the next un-played stage (tracked by the simulation's version), the graph grows
 * by that stage, and the fully replayed graph carries every causal hop the one-shot seed does.
 */
@Transactional
@DisplayName("causal seed: the ransomware replay lands the kill chain stage by stage")
class AttackPathCausalSeedRansomwareReplayTest extends IntegrationTest {

  // The ransomware scenario replays eight kill-chain stages.
  private static final int STAGES = 8;
  private static final int SPREAD = 4;

  @Autowired private AttackPathCausalSeedService causalSeedService;
  @Autowired private AttackPathGraphService graphService;

  @Test
  @DisplayName("each call plays the next stage; the completed graph carries every causal hop")
  void replay_lands_the_kill_chain_one_stage_at_a_time() {
    Tenant tenant = tenantRepository.save(TenantFixture.getTenant("ap-ransomware-replay-tenant"));
    String sim = causalSeedService.createRansomwareSimulation(tenant.getId());

    // Created empty: the intrusion has not started yet.
    assertThat(graphService.buildGraph(sim).attackPathExecutions()).isEmpty();

    // First stage: phishing lands one execution.
    AttackPathReplayStepDTO first =
        causalSeedService.replayRansomwareNextStage(sim, tenant.getId(), SPREAD);
    assertThat(first.stage()).isZero();
    assertThat(first.done()).isFalse();
    assertThat(graphService.buildGraph(sim).attackPathExecutions()).hasSize(1);

    // Each further call advances exactly one stage, up to the last.
    AttackPathReplayStepDTO step = first;
    for (int expected = 1; expected < STAGES; expected++) {
      step = causalSeedService.replayRansomwareNextStage(sim, tenant.getId(), SPREAD);
      assertThat(step.stage()).isEqualTo(expected);
    }
    assertThat(step.done()).as("the final stage completes the replay").isTrue();

    // The fully replayed graph is the whole kill chain: spine, both fan-outs and the convergence.
    long causalHops =
        graphService.buildGraph(sim).attackPathExecutions().stream()
            .filter(
                node ->
                    node.getConsumedFindingKeys() != null
                        && node.getConsumedFindingKeys().stream()
                            .anyMatch(key -> !key.matchedFindingIds().isEmpty()))
            .count();
    assertThat(causalHops)
        .as("replaying the stages resolves the same causal hops as the one-shot seed")
        .isGreaterThanOrEqualTo(3L + SPREAD + 3L + SPREAD + SPREAD);

    // Replaying past the end is a no-op.
    AttackPathReplayStepDTO afterEnd =
        causalSeedService.replayRansomwareNextStage(sim, tenant.getId(), SPREAD);
    assertThat(afterEnd.done()).isTrue();
    assertThat(afterEnd.stage()).isEqualTo(STAGES);
  }
}
