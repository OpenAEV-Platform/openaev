package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.ProductionActiveTables;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code can_access_tenant} lets a platform row of any dual-scope table through when there is no
 * scope, so arming a dual-scope table makes its platform rows readable from a thread that forgot to
 * set a scope, instead of reading nothing. That is the rule for {@code parameters}, whose platform
 * rows every caller reads. For another dual-scope table it is a decision to take when the table is
 * armed, and this guard refuses an armed dual-scope table that no one has decided for.
 */
@DisplayName("A dual-scope table armed in production has its unscoped platform read pinned")
class DualScopeUnscopedPlatformReadGuardTest {

  /**
   * The dual-scope tables armed in production, with the test class that pins their read of a
   * platform row with no scope. A table is added here in the PR that arms it, with a test of that
   * table, or the PR first makes the unscoped read of its platform rows fail.
   */
  private static final Map<String, String> PINNED =
      Map.of("parameters", "io.openaev.rest.settings.PlatformSettingsUnscopedAccessTest");

  static Set<String> unpinned(Set<String> armed, Set<String> dualScope) {
    Set<String> unpinned = new TreeSet<>(armed);
    unpinned.retainAll(dualScope);
    unpinned.removeAll(PINNED.keySet());
    return unpinned;
  }

  @Test
  @DisplayName("given the production list, should arm no dual-scope table that is not pinned")
  void given_theProductionList_should_armNoDualScopeTableThatIsNotPinned() {
    // Act
    Set<String> unpinned =
        unpinned(
            ProductionActiveTables.names(),
            BackgroundEntrypointTenantScopeArchTest.productionTenantTables().dualScope());

    // Assert
    assertThat(unpinned)
        .as(
            "can_access_tenant lets a platform row of every dual-scope table through with no scope."
                + " Decide it for the table, pin it with a test that reads the table with no scope,"
                + " and name that test in PINNED")
        .isEmpty();
  }

  @Test
  @DisplayName("given a dual-scope table armed with no pin, should report it")
  void given_aDualScopeTableArmedWithNoPin_should_reportIt() {
    // Act
    Set<String> unpinned =
        unpinned(Set.of("parameters", "groups", "tags"), Set.of("parameters", "groups", "roles"));

    // Assert
    assertThat(unpinned).containsExactly("groups");
  }

  @Test
  @DisplayName("given the pinned tables, should name a test class that exists")
  void given_thePinnedTables_should_nameATestClassThatExists() throws ClassNotFoundException {
    for (Map.Entry<String, String> pin : PINNED.entrySet()) {
      // Act
      Class<?> test = Class.forName(pin.getValue());

      // Assert
      assertThat(test.getSimpleName()).as(pin.getKey()).endsWith("Test");
    }
  }
}
