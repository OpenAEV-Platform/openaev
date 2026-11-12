package io.openaev.architecture.fixture;

import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** The shapes of a marked class, for the isolation coverage gate. */
public final class TenantIsolationCoverageFixtures {

  private TenantIsolationCoverageFixtures() {}

  /** No pin, no registration: runs the production list through the CI arming. */
  @CoversTenantIsolation({"tags", "documents"})
  public static class MarksAProductionFollower {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags,documents")
  @CoversTenantIsolation("tags")
  public static class MarksOnAProbePin {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @CoversTenantIsolation("documents")
  public static class MarksATableItsPinLeavesOut {}

  @CoversTenantIsolation("tagz")
  public static class MarksATypo {}

  @CoversTenantIsolation({})
  public static class MarksNothing {}

  /** Marks a table that is active nowhere. */
  @CoversTenantIsolation("not_an_active_table")
  public static class MarksAnInactiveTable {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @TestPropertySource(properties = "openaev.tenant.active-tables=documents")
  @CoversTenantIsolation("documents")
  public static class MarksWithTheKeyDeclaredTwice {}

  /** Follows production in a registration and adds its own table on top. */
  @CoversTenantIsolation("tags")
  public static class MarksOnARegistrationHoldingProduction {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
      tables.add("tags");
      registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
    }
  }

  @CoversTenantIsolation("documents")
  public static class MarksATableItsRegistrationLeavesOut {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  /** Reads state the scan cannot provide, so the gate cannot read its list. */
  @CoversTenantIsolation("tags")
  public static class MarksOnARegistrationTheScanCannotEvaluate {
    static String tables;

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> tables.toLowerCase());
    }
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=documents")
  @CoversTenantIsolation("documents")
  public static class MarksWithAnAnnotationAndARegistration {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "documents");
    }
  }

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  public static class PinsTags {}

  /** Inherits the pin of its parent, which is the site that decides its list. */
  @CoversTenantIsolation("tags")
  public static class MarksOnAnInheritedPin extends PinsTags {}

  /** A class that no marker names: the gate leaves it alone. */
  public static class MarksNothingAtAll {}
}
