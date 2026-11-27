package io.openaev.architecture.fixture;

import io.openaev.architecture.CoversTenantIsolation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.annotation.Testable;

/** The shapes of a marked class, for the isolation coverage gate. */
public final class TenantIsolationCoverageFixtures {

  private TenantIsolationCoverageFixtures() {}

  @CoversTenantIsolation("tags")
  public static class MarksTags {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation("documents")
  public static class MarksDocuments {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation("tagz")
  public static class MarksATypo {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation({})
  public static class MarksNothing {
    @Test
    void runs() {}
  }

  /** Marks a table nothing gates. */
  @CoversTenantIsolation("not_a_gated_table")
  public static class MarksAnUngatedTable {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation("tags")
  public abstract static class MarksAnAbstractClass {
    @Test
    void runs() {}
  }

  @Disabled
  @CoversTenantIsolation("tags")
  public static class MarksADisabledClass {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation("tags")
  public static class MarksAClassWithNoTestMethod {
    void helper() {}
  }

  /** The tests sit in nested classes, as in the large API test classes. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseTestsAreNested {
    @Nested
    public class Cases {
      @Test
      void runs() {}
    }
  }

  /** The only nested class that holds a test is disabled. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestsAreInADisabledNestedClass {
    @Disabled
    @Nested
    public class Cases {
      @Test
      void runs() {}
    }
  }

  /** The marked class is nested in a disabled outer class, so JUnit never runs it. */
  @Disabled
  public static class DisablesAClassAroundAMarkedNestedClass {
    @Nested
    @CoversTenantIsolation("tags")
    public class MarksUnderADisabledOuterClass {
      @Test
      void runs() {}
    }
  }

  /** Holds the one test of its child. */
  public abstract static class HoldsATest {
    @Test
    void runs() {}
  }

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsInherited extends HoldsATest {}

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsDisabled {
    @Disabled
    @Test
    void runs() {}
  }

  /** The only nested class that holds a test holds a disabled one. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyNestedTestIsDisabled {
    @Nested
    public class Cases {
      @Disabled
      @Test
      void runs() {}
    }
  }

  /** Disabled, and not inherited: its subclass below still runs. */
  @Disabled
  public static class DisabledParent {}

  @CoversTenantIsolation("tags")
  public static class MarksAClassUnderADisabledParent extends DisabledParent {
    @Test
    void runs() {}
  }

  /** Jupiter discovers a private method as no test: it is never executed. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsPrivate {
    @Test
    private void runs() {}
  }

  /** Jupiter discovers a static method as no test: it is never executed. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsStatic {
    @Test
    static void runs() {}
  }

  /** {@code @Testable} is the Platform's marker: Jupiter runs only its own test annotations. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsABareTestable {
    @Testable
    void runs() {}
  }

  /** Every method is one Jupiter does not run; the class is reported, not counted. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyNestedTestIsPrivate {
    @Nested
    public class Cases {
      @Test
      private void runs() {}
    }
  }

  /** A composed annotation that is meta-annotated {@code @Test}, as the isolation tests may use. */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @Test
  public @interface IsolationTest {}

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsAComposedAnnotation {
    @IsolationTest
    void runs() {}
  }

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsAFactory {
    @TestFactory
    Stream<DynamicTest> runs() {
      return Stream.empty();
    }
  }

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsParameterized {
    @ParameterizedTest
    @ValueSource(strings = "a")
    void runs(String value) {}
  }

  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsRepeated {
    @RepeatedTest(2)
    void runs() {}
  }

  /** The test is protected, which Jupiter discovers. */
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestIsProtected {
    @Test
    protected void runs() {}
  }

  /** A class that no marker names: the gate leaves it alone. */
  public static class MarksNothingAtAll {}
}
