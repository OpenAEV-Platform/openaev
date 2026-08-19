package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms that, under the default test profile (the {@code BULK_SNAPSHOT_EXPORT} preview feature
 * OFF), no {@code snapshot-attack-observation} handler bean is registered — the gating is a
 * {@code @Conditional} on bean registration, not a branch inside {@code fetch(...)} (PRD Story 1.3
 * AC3/AC5).
 *
 * <p>Deliberately does NOT {@code @Autowired} {@link
 * io.openaev.engine.model.snapshotobservation.AttackObservationHandler}: the point of this test is
 * that the bean does not exist.
 */
@Transactional
@WithMockUser
@DisplayName("AttackObservationHandler under the default profile (flag OFF)")
class AttackObservationIndexingFlagOffTest extends IntegrationTest {

  @Autowired private EngineContext engineContext;

  @Test
  @DisplayName("no snapshot-attack-observation model is registered")
  void given_flagOff_should_notRegisterAttackObservationModel() {
    assertThat(engineContext.getModels())
        .noneMatch(model -> "snapshot-attack-observation".equals(model.getName()));
  }
}
