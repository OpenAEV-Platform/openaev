package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.architecture.fixture.PinnedActiveTablesFixtures;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.InheritsThePin;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsAnEmptyList;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsNothing;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughAComposedAnnotation;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughSpringBootTestWithColon;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughTestPropertySource;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.SetsAnotherProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the pinned-list guard finds every shape of pin, tells a class that follows production from
 * one that does not, and rejects a baseline line nobody can trust. {@link
 * PinnedActiveTablesArchTest} runs the same catalog over the test tree.
 */
@DisplayName("Pinned activation list guard: it finds pins, it reads the baseline strictly")
class PinnedActiveTablesRulesTest {

  private static final String GOOD_REASON =
      "probe: arms only the table under test and asserts nothing about any other table";

  @Nested
  @DisplayName("Annotation pins")
  class AnnotationPins {

    @Test
    @DisplayName(
        "given every annotation shape, should report each pin once at the class that wrote it")
    void given_everyAnnotationShape_should_reportEachPinOnceAtItsDeclaringClass() {
      // Arrange
      List<Class<?>> classes =
          List.of(
              PinsThroughTestPropertySource.class,
              InheritsThePin.class,
              PinsThroughSpringBootTestWithColon.class,
              PinsThroughAComposedAnnotation.class,
              PinsAnEmptyList.class,
              SetsAnotherProperty.class,
              PinsNothing.class);

      // Act
      Map<String, Set<String>> pins = PinnedActiveTablesCatalog.annotationPins(classes);

      // Assert
      assertThat(pins)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  PinsThroughTestPropertySource.class.getName(), Set.of("documents", "tags"),
                  PinsThroughSpringBootTestWithColon.class.getName(), Set.of("assets", "findings"),
                  PinsThroughAComposedAnnotation.class.getName(), Set.of("secrets"),
                  PinsAnEmptyList.class.getName(), Set.of()));
    }

    @Test
    @DisplayName("given the fixture holder, should not report a class that pins nothing")
    void given_fixtureHolder_should_notReportAClassThatPinsNothing() {
      // Act
      Map<String, Set<String>> pins =
          PinnedActiveTablesCatalog.annotationPins(List.of(PinnedActiveTablesFixtures.class));

      // Assert
      assertThat(pins).isEmpty();
    }
  }

  @Nested
  @DisplayName("Dynamic pins")
  class DynamicPins {

    @Test
    @DisplayName(
        "given a dynamic registration, should report it unless it reads the production list")
    void given_dynamicRegistration_should_reportItUnlessItReadsTheProductionList(@TempDir Path root)
        throws IOException {
      // Arrange
      write(
          root,
          "io/openaev/a/FollowsProductionTest.java",
          """
          class FollowsProductionTest {
            @DynamicPropertySource
            static void tables(DynamicPropertyRegistry registry) {
              registry.add(
                  "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
            }
          }
          """);
      write(
          root,
          "io/openaev/b/HardCodesTest.java",
          """
          class HardCodesTest {
            @DynamicPropertySource
            static void tables(DynamicPropertyRegistry registry) {
              registry.add("openaev.tenant.active-tables", () -> "tags");
            }
          }
          """);
      write(
          root,
          "io/openaev/c/OwnParserTest.java",
          """
          class OwnParserTest {
            @DynamicPropertySource
            static void tables(DynamicPropertyRegistry registry) {
              Properties props = new Properties();
              props.load(new FileInputStream("src/main/resources/application.properties"));
              registry.add("openaev.tenant.active-tables", () -> props.getProperty("x"));
            }
          }
          """);
      write(
          root,
          "io/openaev/d/OtherPropertyTest.java",
          """
          class OtherPropertyTest {
            @DynamicPropertySource
            static void urls(DynamicPropertyRegistry registry) {
              registry.add("openaev.other.url", () -> "http://localhost");
            }
          }
          """);
      write(
          root,
          "io/openaev/e/OnlyMentionsItTest.java",
          """
          /** Read {@code openaev.tenant.active-tables} for the list. */
          class OnlyMentionsItTest {}
          """);

      // Act
      Set<String> pins = PinnedActiveTablesCatalog.dynamicPins(root);

      // Assert
      assertThat(pins)
          .containsExactlyInAnyOrder("io.openaev.b.HardCodesTest", "io.openaev.c.OwnParserTest");
    }

    private void write(Path root, String relative, String source) throws IOException {
      Path file = root.resolve(relative);
      Files.createDirectories(file.getParent());
      Files.writeString(file, source);
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given well-formed lines, should parse class, tables and kind")
    void given_wellFormedLines_should_parseClassTablesAndKind() {
      // Act
      List<PinnedActiveTablesBaseline.Entry> entries =
          PinnedActiveTablesBaseline.parse(
              List.of(
                  "# a comment",
                  "",
                  "io.openaev.A | tags,documents | " + GOOD_REASON,
                  "io.openaev.B | none | control: leaves every table off to measure the v1 filter alone",
                  "io.openaev.C | dynamic | own-reader: parses application.properties itself instead of the shared reader"));

      // Assert
      assertThat(entries)
          .extracting(
              PinnedActiveTablesBaseline.Entry::testClass,
              PinnedActiveTablesBaseline.Entry::tables,
              PinnedActiveTablesBaseline.Entry::kind)
          .containsExactly(
              tuple("io.openaev.A", Set.of("documents", "tags"), "probe"),
              tuple("io.openaev.B", Set.of(), "control"),
              tuple("io.openaev.C", null, "own-reader"));
      assertThat(PinnedActiveTablesBaseline.malformed(entries)).isEmpty();
    }

    @Test
    @DisplayName("given lines nobody can trust, should name each problem")
    void given_untrustworthyLines_should_nameEachProblem() {
      // Arrange
      List<PinnedActiveTablesBaseline.Entry> entries =
          PinnedActiveTablesBaseline.parse(
              List.of(
                  "io.openaev.NoReason | tags",
                  "io.openaev.UnknownKind | tags | fine: it is fine and it will always stay fine",
                  "io.openaev.ShortWhy | tags | probe: arms tags",
                  "io.openaev.Dup | tags | " + GOOD_REASON,
                  "io.openaev.Dup | tags | " + GOOD_REASON,
                  "not a class | tags | " + GOOD_REASON));

      // Act
      List<String> problems = PinnedActiveTablesBaseline.malformed(entries);

      // Assert
      assertThat(problems)
          .hasSize(5)
          .anySatisfy(p -> assertThat(p).contains("NoReason"))
          .anySatisfy(p -> assertThat(p).contains("UnknownKind"))
          .anySatisfy(p -> assertThat(p).contains("ShortWhy"))
          .anySatisfy(p -> assertThat(p).contains("duplicates"))
          .anySatisfy(p -> assertThat(p).contains("not a class"));
    }
  }
}
