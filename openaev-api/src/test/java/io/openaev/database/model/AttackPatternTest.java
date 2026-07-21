package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttackPatternTest {

  private static final Instant SENTINEL = Instant.EPOCH;

  private static KillChainPhase killChainPhase(String id) {
    KillChainPhase phase = new KillChainPhase();
    phase.setId(id);
    return phase;
  }

  private static AttackPattern attackPatternAtSentinel() {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setKillChainPhases(
        new ArrayList<>(List.of(killChainPhase("k1"), killChainPhase("k2"))));
    attackPattern.setUpdatedAt(SENTINEL);
    return attackPattern;
  }

  @Test
  @DisplayName(
      "setKillChainPhases with the same phase ids does not bump updatedAt, so no-op collector"
          + " upserts do not force an UPDATE and an SSE restream (#6778)")
  void setKillChainPhases_same_ids_does_not_bump() {
    AttackPattern attackPattern = attackPatternAtSentinel();

    attackPattern.setKillChainPhases(
        new ArrayList<>(List.of(killChainPhase("k2"), killChainPhase("k1"))));

    assertThat(attackPattern.getUpdatedAt()).isEqualTo(SENTINEL);
  }

  @Test
  @DisplayName("setKillChainPhases with different phase ids bumps updatedAt")
  void setKillChainPhases_different_ids_bumps() {
    AttackPattern attackPattern = attackPatternAtSentinel();

    attackPattern.setKillChainPhases(new ArrayList<>(List.of(killChainPhase("k3"))));

    assertThat(attackPattern.getUpdatedAt()).isAfter(SENTINEL);
  }
}
