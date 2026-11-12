package io.openaev.architecture.fixture;

import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** The shapes of a marked class, for the isolation coverage gate. */
public final class TenantIsolationCoverageFixtures {

  private TenantIsolationCoverageFixtures() {}

  /** No pin, no registration: the gating CI run arms no table for it. */
  @CoversTenantIsolation({"tags", "documents"})
  public static class MarksWithNoDeclaration {
    @Test
    void runs() {}
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags,documents")
  @CoversTenantIsolation("tags")
  public static class MarksOnAProbePin {
    @Test
    void runs() {}
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("documents")
  public static class MarksATableItsPinLeavesOut {
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

  /** Marks a table that is active nowhere. */
  @CoversTenantIsolation("not_an_active_table")
  public static class MarksAnInactiveTable {
    @Test
    void runs() {}
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @TestPropertySource(properties = "openaev.tenant.active-tables=documents")
  @CoversTenantIsolation("documents")
  public static class MarksWithTheKeyDeclaredTwice {
    @Test
    void runs() {}
  }

  /** Follows production in a registration and adds its own table on top. */
  @CoversTenantIsolation({"tags", "documents"})
  public static class MarksOnARegistrationHoldingProduction {
    @Test
    void runs() {}

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
      tables.addAll(Set.of("tags", "documents"));
      registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
    }
  }

  @CoversTenantIsolation("documents")
  public static class MarksATableItsRegistrationLeavesOut {
    @Test
    void runs() {}

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  /** Reads state the scan cannot provide, so the gate cannot read its list. */
  @CoversTenantIsolation("tags")
  public static class MarksOnARegistrationTheScanCannotEvaluate {
    @Test
    void runs() {}

    static String tables;

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> tables.toLowerCase());
    }
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=documents")
  @CoversTenantIsolation("documents")
  public static class MarksWithAnAnnotationAndARegistration {
    @Test
    void runs() {}

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "documents");
    }
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  public static class PinsTags {}

  /** Inherits the pin of its parent, which is the site that decides its list. */
  @CoversTenantIsolation("tags")
  public static class MarksOnAnInheritedPin extends PinsTags {
    @Test
    void runs() {}
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=*")
  @CoversTenantIsolation("tags")
  public static class MarksOnAWildcardPin {
    @Test
    void runs() {}
  }

  /** Registers the key; its child registers it too, and Spring resolves one of the two. */
  public static class RegistersTags {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  @CoversTenantIsolation("tags")
  public static class MarksWithAParentAndAChildRegistering extends RegistersTags {
    @Test
    void runs() {}

    @DynamicPropertySource
    static void childTables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags,documents");
    }
  }

  /** The pin of the enclosing class reaches the nested class marked below. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  public static class PinsTagsAroundANestedClass {
    @Nested
    @CoversTenantIsolation("tags")
    public class MarksUnderTheEnclosingPin {
      @Test
      void runs() {}
    }
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("tags")
  public abstract static class MarksAnAbstractClass {
    @Test
    void runs() {}
  }

  @Disabled
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("tags")
  public static class MarksADisabledClass {
    @Test
    void runs() {}
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("tags")
  public static class MarksAClassWithNoTestMethod {
    void helper() {}
  }

  /** The tests sit in nested classes, as in the large API test classes. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseTestsAreNested {
    @Nested
    public class Cases {
      @Test
      void runs() {}
    }
  }

  /** The only nested class that holds a test is disabled. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("tags")
  public static class MarksAClassWhoseOnlyTestsAreInADisabledNestedClass {
    @Disabled
    @Nested
    public class Cases {
      @Test
      void runs() {}
    }
  }

  /** A class that no marker names: the gate leaves it alone. */
  public static class MarksNothingAtAll {}
}
