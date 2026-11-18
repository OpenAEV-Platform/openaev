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

  /** Spring keeps the last repeated declaration, documents. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  @TestPropertySource(properties = "openaev.tenant.active-tables=documents")
  public static class DeclaresTheKeyTwice {}

  /** Spring keeps the subclass's value over the one it inherits. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=findings")
  public static class OverridesAnInheritedPin extends PinsThroughTestPropertySource {}

  @TestPropertySource(properties = "openaev.marking.active-tables=assets")
  public static class SetsAnotherProperty {}

  public static class PinsNothing {}

  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  public interface PinsThroughAnInterface {}

  public interface LeftBranch extends PinsThroughAnInterface {}

  public interface RightBranch extends PinsThroughAnInterface {}

  /** Reaches one declaration through two interfaces: one pin, not a conflict. */
  public static class InheritsThroughADiamond implements LeftBranch, RightBranch {}

  /** A nested class is part of the class that declares the pin, so it does not inherit it. */
  @TestPropertySource(properties = "openaev.tenant.active-tables=tags")
  public static class PinsWithAnInnerClass {
    public class Inner {}
  }

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

  /** Registers the terminal wildcard, which production reads as every table it arms. */
  public static class RegistersTheWildcard {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> " * ");
    }
  }

  /** Production refuses the wildcard beside a table at startup. */
  public static class RegistersTheWildcardBesideATable {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "*,tags");
    }
  }

  /** Runs with the registration its parent makes. */
  public static class InheritsARegisteredPin extends SubtractsOneTable {}

  public static class InheritsAFollower extends FollowsProduction {}

  /** Spring runs a static registration declared on an interface for every class implementing it. */
  public interface RegistersThroughAnInterface {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.active-tables", () -> "tags");
    }
  }

  public static class ImplementsARegisteringInterface implements RegistersThroughAnInterface {}

  public static class RegistersARelaxedName {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.tenant.activeTables", () -> "tags");
    }
  }

  /** Spring Boot ignores the case of each element, so this is the key. */
  public static class RegistersAnUppercaseElement {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("openaev.TENANT.active-tables", () -> "tags");
    }
  }

  /** Spring Boot ignores every character but letters and digits inside an element. */
  public static class RegistersWithIgnoredCharacters {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("open_aev..ten-ant.active tables", () -> "tags");
    }
  }

  /** Spring Boot reads a bracketed element as the element it encloses. */
  public static class RegistersIndexedElements {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      registry.add("[openaev][tenant][activetables]", () -> "tags");
    }
  }

  /** One element, not two, so another property: the scan has no reason to run it. */
  public static class RegistersADashedNameThatIsNotTheKey {
    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
      registry.add("openaev-tenant.url", () -> "http://localhost");
      throw new IllegalStateException("a registration that never reaches the key is not run");
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

  /** Hands its registration to a shared helper of another class, through a method of its own. */
  public static class DelegatesToAHelper {
    @DynamicPropertySource
    static void tables(DynamicPropertyRegistry registry) {
      arm(registry);
    }

    private static void arm(DynamicPropertyRegistry registry) {
      ActiveTablesHelper.armOnly(registry, "tags");
    }
  }

  /** Calls a helper that never registers the key, so the scan has no reason to run it. */
  public static class DelegatesAnotherPropertyToAHelper {
    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
      OtherPropertyHelper.registerUrl(registry);
      throw new IllegalStateException("a registration that never reaches the key is not run");
    }
  }

  static final class ActiveTablesHelper {
    static void armOnly(DynamicPropertyRegistry registry, String tables) {
      registry.add(Keys.ACTIVE_TABLES, () -> tables);
    }

    private ActiveTablesHelper() {}
  }

  static final class OtherPropertyHelper {
    static void registerUrl(DynamicPropertyRegistry registry) {
      registry.add("openaev.other-url", () -> "http://localhost");
    }

    private OtherPropertyHelper() {}
  }
}
