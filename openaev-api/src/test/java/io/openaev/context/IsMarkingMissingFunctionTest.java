package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truth table of {@code is_marking_missing}: true means "the caller does not hold this marking", so
 * fail-closed here is returning <b>true</b> — with no clearance every marking is missing and every
 * marked row is hidden.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("is_marking_missing SQL function")
class IsMarkingMissingFunctionTest {

  @Autowired private EntityManager entityManager;

  private void setClearance(String clearance) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_markings', :scope, true)")
        .setParameter("scope", clearance)
        .getSingleResult();
  }

  private boolean isMissing(String markingId) {
    return (Boolean)
        entityManager
            .createNativeQuery("SELECT is_marking_missing(:mid)")
            .setParameter("mid", markingId)
            .getSingleResult();
  }

  @Test
  @DisplayName("with no clearance every marking is missing")
  void noClearanceMissesEverything() {
    assertTrue(isMissing("m1"));
  }

  @Test
  @DisplayName("an empty clearance behaves like no clearance")
  void emptyClearanceMissesEverything() {
    setClearance("");
    assertTrue(isMissing("m1"));
  }

  @Test
  @DisplayName("a held marking is not missing, one outside the clearance is")
  void singleMarking() {
    setClearance("m1");
    assertFalse(isMissing("m1"));
    assertTrue(isMissing("m2"));
  }

  @Test
  @DisplayName("every marking of a multi-valued clearance is held")
  void multipleMarkings() {
    setClearance("m1,m2");
    assertFalse(isMissing("m1"));
    assertFalse(isMissing("m2"));
    assertTrue(isMissing("m3"));
  }

  @Test
  @DisplayName("matches marking ids exactly, never as a prefix or substring")
  void exactMatchOnly() {
    setClearance("m1");
    assertTrue(isMissing("m10"));
    assertTrue(isMissing("1"));
  }

  @Test
  @DisplayName("a null marking id counts as missing, so it can never widen visibility")
  void nullMarkingCountsAsMissing() {
    // Without the COALESCE this would be NULL: the inner WHERE of the anti-join would drop the row,
    // EXISTS would be false and the marked row would become visible to everyone.
    setClearance("m1");
    assertTrue(
        (Boolean)
            entityManager.createNativeQuery("SELECT is_marking_missing(NULL)").getSingleResult());
  }
}
