package io.openaev.architecture.fixture;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.openaev.config.ProductionActiveTables;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** The shapes in which a test class can pin the activation list, for the pinned-list guard. */
public final class PinnedActiveTablesFixtures {

  private PinnedActiveTablesFixtures() {}

  // -- ANNOTATION SHAPES --

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags,documents")
  public static class PinsThroughTestPropertySource {}

  /** Inherits the pin: the site is the parent that wrote it, not this class. */
  public static class InheritsThePin extends PinsThroughTestPropertySource {}

  @SpringBootTest(
      properties = {"openaev.other=1", "openaev.tenant.active-tables: assets , findings"})
  public static class PinsThroughSpringBootTestWithColon {}

  @Retention(RetentionPolicy.RUNTIME)
  @TestPropertySource(properties = "openaev.tenant.active-tables=" + "secrets")
  public @interface ComposedPin {}

  @ComposedPin
  public static class PinsThroughAComposedAnnotation {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=")
  public static class PinsAnEmptyList {}

  /** Spring Boot binds the relaxed spelling to the same key. */
  @TestPropertySource(properties = "openaev.tenant.activeTables=channels")
  public static class PinsThroughARelaxedName {}

  @TestPropertySource(properties = "openaev.marking.active-tables=assets")
  public static class SetsAnotherProperty {}

  public static class PinsNothing {}

  // -- DYNAMIC SHAPES --

  public static class FollowsProduction {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add(
          "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
    }
  }

  public static class FollowsProductionWithItsOwnTables {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
      tables.add("injects");
      registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
    }
  }

  public static class SubtractsOneTable {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
      tables.remove("documents");
      registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
    }
  }

  /**
   * Names the production reader only in a comment and a text block: ProductionActiveTables.names().
   */
  public static class NamesTheReaderOnlyInText {
    static final String NOTE =
        """
        to migrate: ProductionActiveTables.names()
        """;

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      // ProductionActiveTables.names() would be the right base here
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  public static class AssumesOnTheReaderThenHardCodes {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      assumeTrue(ProductionActiveTables.names().contains("tags"));
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  /** The key held in a constant: the compiler copies it into this class. */
  public static class RegistersThroughAConstant {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add(Keys.ACTIVE_TABLES, () -> "tags");
    }
  }

  public static class RegistersARelaxedName {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.activeTables", () -> "tags");
    }
  }

  /** Reads state the scan cannot provide, so its value is unknown when the guard runs. */
  public static class RegistersStateSetLater {
    static String tables;

    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> tables.toLowerCase());
    }
  }

  public static class RegistersTheKeyTwice {
    @DynamicPropertySource
    static void production(DynamicPropertyRegistry registry) {
      registry.add(
          "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
    }

    @DynamicPropertySource
    static void tags(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  public static class RegistersAnotherProperty {
    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.other-url", () -> "http://localhost");
    }
  }

  static final class Keys {
    static final String ACTIVE_TABLES = "openaev.tenant.active-tables";

    private Keys() {}
  }
}
