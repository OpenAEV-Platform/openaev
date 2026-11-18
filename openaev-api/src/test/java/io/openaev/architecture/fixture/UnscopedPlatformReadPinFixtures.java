package io.openaev.architecture.fixture;

import io.openaev.utilstest.PinsUnscopedPlatformRead;
import io.openaev.utilstest.WithoutTenantScope;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test classes in each shape the pin guard must tell apart. They are never run: this class is not a
 * test, and surefire does not pick up its nested classes.
 */
public class UnscopedPlatformReadPinFixtures {

  /** A real pin: an enabled test, with no scope, marked with its table. */
  @WithoutTenantScope
  public static class PinsParameters {
    @Test
    @PinsUnscopedPlatformRead(table = "parameters")
    void readsWithNoScope() {}
  }

  /** The pin sits in a nested class of a class that runs with no scope. */
  @WithoutTenantScope
  public static class PinsParametersInANestedClass {
    @Nested
    class Reads {
      @Test
      @PinsUnscopedPlatformRead(table = "parameters")
      void readsWithNoScope() {}
    }
  }

  /** A test with no marker. */
  @WithoutTenantScope
  public static class MarksNothing {
    @Test
    void readsWithNoScope() {}
  }

  /** The marker is on a method that is not a test. */
  @WithoutTenantScope
  public static class MarksAHelper {
    @PinsUnscopedPlatformRead(table = "parameters")
    void helper() {}
  }

  /** The marked test is disabled. */
  @WithoutTenantScope
  public static class MarksADisabledTest {
    @Test
    @Disabled
    @PinsUnscopedPlatformRead(table = "parameters")
    void readsWithNoScope() {}
  }

  /** The marked test runs with the default tenant scope, so its read is not unscoped. */
  public static class MarksAScopedTest {
    @Test
    @PinsUnscopedPlatformRead(table = "parameters")
    void readsWithTheDefaultScope() {}
  }
}
