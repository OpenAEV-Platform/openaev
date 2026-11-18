package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.fixture.UnscopedPlatformReadPinFixtures;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utilstest.PinsUnscopedPlatformRead;
import io.openaev.utilstest.WithoutTenantScope;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * {@code can_access_tenant} lets a platform row of any dual-scope table through when there is no
 * scope, so arming a dual-scope table makes its platform rows readable from a thread that forgot to
 * set a scope, instead of reading nothing. That is the rule for {@code parameters}, whose platform
 * rows every caller reads. For another dual-scope table it is a decision to take when the table is
 * armed, and this guard refuses an armed dual-scope table that no one has decided for.
 *
 * <p>A table is pinned by a test method marked {@link PinsUnscopedPlatformRead} for that table, in
 * the class named for it: an enabled test that runs under {@link WithoutTenantScope}. Naming a
 * class is not enough.
 */
@DisplayName("A dual-scope table armed in production has its unscoped platform read pinned")
class DualScopeUnscopedPlatformReadGuardTest {

  /**
   * The dual-scope tables armed in production, with the test class whose marked test pins their
   * read of a platform row with no scope. A table is added here in the PR that arms it, with a test
   * of that table carrying {@link PinsUnscopedPlatformRead}, or the PR first makes the unscoped
   * read of its platform rows fail.
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

  /**
   * Whether {@code test}, or a class nested in it, has a test marked as the pin of {@code table}.
   */
  static boolean pins(Class<?> test, String table) {
    for (Method method : test.getDeclaredMethods()) {
      PinsUnscopedPlatformRead marker = method.getAnnotation(PinsUnscopedPlatformRead.class);
      if (marker != null
          && marker.table().equals(table)
          && AnnotationSupport.isAnnotated(method, Test.class)
          && !AnnotationSupport.isAnnotated(method, Disabled.class)
          && runsWithNoScope(method)) {
        return true;
      }
    }
    for (Class<?> nested : test.getDeclaredClasses()) {
      if (pins(nested, table)) {
        return true;
      }
    }
    return false;
  }

  private static boolean runsWithNoScope(Method method) {
    if (AnnotationSupport.isAnnotated(method, WithoutTenantScope.class)) {
      return true;
    }
    for (Class<?> c = method.getDeclaringClass(); c != null; c = c.getEnclosingClass()) {
      if (AnnotationSupport.isAnnotated(c, WithoutTenantScope.class)) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("given the pinned tables, should name a class with a test marked for that table")
  void given_thePinnedTables_should_nameAClassWithATestMarkedForThatTable()
      throws ClassNotFoundException {
    for (Map.Entry<String, String> pin : PINNED.entrySet()) {
      // Act
      Class<?> test = Class.forName(pin.getValue());

      // Assert
      assertThat(pins(test, pin.getKey()))
          .as(
              "%s must hold an enabled test marked @PinsUnscopedPlatformRead(table = \"%s\"), run"
                  + " under @WithoutTenantScope",
              pin.getValue(), pin.getKey())
          .isTrue();
    }
  }

  @Test
  @DisplayName("given a class with no test marked for the table, should not pin it")
  void given_aClassWithNoTestMarkedForTheTable_should_notPinIt() {
    // Assert
    assertThat(pins(UnscopedPlatformReadPinFixtures.PinsParameters.class, "parameters")).isTrue();
    assertThat(pins(UnscopedPlatformReadPinFixtures.PinsParameters.class, "groups")).isFalse();
    assertThat(pins(UnscopedPlatformReadPinFixtures.MarksNothing.class, "parameters")).isFalse();
  }

  @Test
  @DisplayName("given a pin in a nested class, should find it")
  void given_aPinInANestedClass_should_findIt() {
    // Assert
    assertThat(
            pins(UnscopedPlatformReadPinFixtures.PinsParametersInANestedClass.class, "parameters"))
        .isTrue();
  }

  @Test
  @DisplayName("given a marker that is not an enabled test run with no scope, should not pin")
  void given_aMarkerThatIsNotAnEnabledUnscopedTest_should_notPin() {
    // Assert
    assertThat(pins(UnscopedPlatformReadPinFixtures.MarksAHelper.class, "parameters")).isFalse();
    assertThat(pins(UnscopedPlatformReadPinFixtures.MarksADisabledTest.class, "parameters"))
        .isFalse();
    assertThat(pins(UnscopedPlatformReadPinFixtures.MarksAScopedTest.class, "parameters"))
        .isFalse();
  }
}
