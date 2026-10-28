package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.architecture.PinnedActiveTablesCatalog.Registration;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.AssumesOnTheReaderThenHardCodes;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.FollowsProduction;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.FollowsProductionWithItsOwnTables;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.InheritsThePin;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.NamesTheReaderOnlyInText;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsAnEmptyList;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsNothing;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughAComposedAnnotation;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughARelaxedName;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughSpringBootTestWithColon;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.PinsThroughTestPropertySource;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.RegistersARelaxedName;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.RegistersAnotherProperty;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.RegistersStateSetLater;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.RegistersTheKeyTwice;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.RegistersThroughAConstant;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.SetsAnotherProperty;
import io.openaev.architecture.fixture.PinnedActiveTablesFixtures.SubtractsOneTable;
import io.openaev.config.ProductionActiveTables;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the pinned-list guard finds every shape of pin, judges a dynamic registration by the value
 * it registers, and rejects a baseline line nobody can trust. {@link PinnedActiveTablesArchTest}
 * runs the same catalog over the test tree.
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
              PinsThroughARelaxedName.class,
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
                  PinsAnEmptyList.class.getName(), Set.of(),
                  PinsThroughARelaxedName.class.getName(), Set.of("channels")));
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

    private final Set<String> production = ProductionActiveTables.names();

    @Test
    @DisplayName("given a registration holding the production list, should not report it as a pin")
    void given_registrationHoldingTheProductionList_should_notReportItAsAPin() {
      // Act
      Map<String, Registration> registrations =
          PinnedActiveTablesCatalog.dynamicRegistrations(
              List.of(FollowsProduction.class, FollowsProductionWithItsOwnTables.class));

      // Assert
      assertThat(registrations)
          .containsOnlyKeys(
              FollowsProduction.class.getName(), FollowsProductionWithItsOwnTables.class.getName());
      assertThat(PinnedActiveTablesCatalog.dynamicPins(registrations, production)).isEmpty();
    }

    @Test
    @DisplayName(
        "given the production list minus one table, should report the pin with what it registers")
    void given_productionListMinusOneTable_should_reportThePinWithWhatItRegisters() {
      // Arrange
      Set<String> expected = new TreeSet<>(production);
      expected.remove("documents");

      // Act
      Map<String, Registration> pins =
          PinnedActiveTablesCatalog.dynamicPins(
              PinnedActiveTablesCatalog.dynamicRegistrations(List.of(SubtractsOneTable.class)),
              production);

      // Assert
      assertThat(pins)
          .containsExactly(Map.entry(SubtractsOneTable.class.getName(), Registration.of(expected)));
    }

    @Test
    @DisplayName(
        "given a hard-coded value beside a mention or a use of the reader, should report it")
    void given_hardCodedValueBesideAMentionOrAUseOfTheReader_should_reportIt() {
      // Act
      Map<String, Registration> pins =
          PinnedActiveTablesCatalog.dynamicPins(
              PinnedActiveTablesCatalog.dynamicRegistrations(
                  List.of(
                      NamesTheReaderOnlyInText.class,
                      AssumesOnTheReaderThenHardCodes.class,
                      RegistersThroughAConstant.class,
                      RegistersARelaxedName.class)),
              production);

      // Assert
      assertThat(pins)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  NamesTheReaderOnlyInText.class.getName(), Registration.of(Set.of("tags")),
                  AssumesOnTheReaderThenHardCodes.class.getName(), Registration.of(Set.of("tags")),
                  RegistersThroughAConstant.class.getName(), Registration.of(Set.of("tags")),
                  RegistersARelaxedName.class.getName(), Registration.of(Set.of("tags"))));
    }

    @Test
    @DisplayName("given a registration whose value cannot be read, should report it as unevaluated")
    void given_registrationWhoseValueCannotBeRead_should_reportItAsUnevaluated() {
      // Act
      Map<String, Registration> pins =
          PinnedActiveTablesCatalog.dynamicPins(
              PinnedActiveTablesCatalog.dynamicRegistrations(
                  List.of(RegistersStateSetLater.class, RegistersTheKeyTwice.class)),
              production);

      // Assert
      assertThat(pins)
          .containsOnlyKeys(
              RegistersStateSetLater.class.getName(), RegistersTheKeyTwice.class.getName());
      assertThat(pins.values()).noneMatch(Registration::evaluated);
    }

    @Test
    @DisplayName("given a registration of another property, should not report it")
    void given_registrationOfAnotherProperty_should_notReportIt() {
      // Act
      Map<String, Registration> registrations =
          PinnedActiveTablesCatalog.dynamicRegistrations(List.of(RegistersAnotherProperty.class));

      // Assert
      assertThat(registrations).isEmpty();
    }
  }

  @Nested
  @DisplayName("The baseline")
  class TheBaseline {

    @Test
    @DisplayName("given well-formed lines, should parse class, tables, kind and expiry")
    void given_wellFormedLines_should_parseClassTablesKindAndExpiry() {
      // Act
      List<PinnedActiveTablesBaseline.Entry> entries =
          PinnedActiveTablesBaseline.parse(
              List.of(
                  "# a comment",
                  "",
                  "io.openaev.A | tags,documents | " + GOOD_REASON,
                  "io.openaev.B | none | control until-active:scenarios,injects: leaves every"
                      + " table off to measure the v1 filter alone",
                  "io.openaev.C | unevaluated | drifts: registers a value read from a field that"
                      + " the scan cannot fill"));

      // Assert
      assertThat(entries)
          .extracting(
              PinnedActiveTablesBaseline.Entry::testClass,
              PinnedActiveTablesBaseline.Entry::tables,
              PinnedActiveTablesBaseline.Entry::kind,
              PinnedActiveTablesBaseline.Entry::untilActive)
          .containsExactly(
              tuple("io.openaev.A", Set.of("documents", "tags"), "probe", Set.of()),
              tuple("io.openaev.B", Set.of(), "control", Set.of("injects", "scenarios")),
              tuple("io.openaev.C", null, "drifts", Set.of()));
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
                  "io.openaev.RetiredKind | tags | own-reader: parses the properties file with"
                      + " its own copy of the parser",
                  "io.openaev.ShortWhy | tags | probe: arms tags",
                  "io.openaev.ControlWithoutExpiry | tags | control: leaves documents off to"
                      + " show the simulation lookup refuses alone",
                  "io.openaev.Dup | tags | " + GOOD_REASON,
                  "io.openaev.Dup | tags | " + GOOD_REASON,
                  "not a class | tags | " + GOOD_REASON));

      // Act
      List<String> problems = PinnedActiveTablesBaseline.malformed(entries);

      // Assert
      assertThat(problems)
          .hasSize(7)
          .anySatisfy(p -> assertThat(p).contains("NoReason"))
          .anySatisfy(p -> assertThat(p).contains("UnknownKind"))
          .anySatisfy(p -> assertThat(p).contains("RetiredKind"))
          .anySatisfy(p -> assertThat(p).contains("ShortWhy"))
          .anySatisfy(p -> assertThat(p).contains("ControlWithoutExpiry").contains("until-active"))
          .anySatisfy(p -> assertThat(p).contains("duplicates"))
          .anySatisfy(p -> assertThat(p).contains("not a class"));
    }

    @Test
    @DisplayName("given a line whose until-active table production arms, should report it expired")
    void given_lineWhoseUntilActiveTableProductionArms_should_reportItExpired() {
      // Arrange
      List<PinnedActiveTablesBaseline.Entry> entries =
          PinnedActiveTablesBaseline.parse(
              List.of(
                  "io.openaev.Expired | tags | control until-active:scenarios,documents: leaves"
                      + " documents off to show the scenario filter holds alone",
                  "io.openaev.Pending | tags | control until-active:scenarios: leaves scenarios"
                      + " off so the v1 filter decides alone"));

      // Act
      List<String> expired =
          PinnedActiveTablesBaseline.expired(entries, Set.of("documents", "tags"));

      // Assert
      assertThat(expired)
          .singleElement()
          .satisfies(p -> assertThat(p).contains("io.openaev.Expired").endsWith("documents"));
    }
  }
}
