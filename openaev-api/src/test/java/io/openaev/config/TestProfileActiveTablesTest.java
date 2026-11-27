package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Holds the activation list of the test profile equal to production's. Every integration test that
 * declares no list of its own runs on the profile's, which shadows {@code application.properties}
 * on the test classpath. A profile that arms fewer tables than production leaves a path through the
 * missing table with no isolation in the tests while production runs it under the inspector, and
 * nothing else goes red.
 */
@DisplayName("The test profile arms the tables production arms")
class TestProfileActiveTablesTest {

  /** One line per table the two lists do not share, empty when they arm the same tables. */
  static List<String> drift(Set<String> production, Set<String> profile) {
    List<String> drift = new ArrayList<>();
    new TreeSet<>(production)
        .stream()
            .filter(table -> !profile.contains(table))
            .forEach(
                table -> drift.add("production arms '" + table + "', the test profile does not"));
    new TreeSet<>(profile)
        .stream()
            .filter(table -> !production.contains(table))
            .forEach(
                table -> drift.add("the test profile arms '" + table + "', production does not"));
    return drift;
  }

  @Nested
  @DisplayName("The two lists on disk")
  class TheTwoLists {

    @Test
    @DisplayName(
        "given the production list, should find it non-empty so the comparison means something")
    void given_productionList_should_beNonEmpty() {
      // Act
      Set<String> production = ProductionActiveTables.names();

      // Assert
      assertThat(production).isNotEmpty();
    }

    @Test
    @DisplayName("given both files, should arm the same tables")
    void given_bothFiles_should_armTheSameTables() {
      // Act
      List<String> drift =
          drift(ProductionActiveTables.names(), ProductionActiveTables.testProfileNames());

      // Assert
      assertThat(drift)
          .as(
              "src/test/resources/application.properties must hold the production list of"
                  + " src/main/resources/application.properties: copy the line")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("The comparison")
  class TheComparison {

    @Test
    @DisplayName("given the same tables, should find no drift")
    void given_sameTables_should_findNoDrift() {
      // Act
      List<String> drift = drift(Set.of("tags", "teams"), Set.of("teams", "tags"));

      // Assert
      assertThat(drift).isEmpty();
    }

    @Test
    @DisplayName("given a profile that lacks a production table, should name it")
    void given_profileThatLacksAProductionTable_should_nameIt() {
      // Act
      List<String> drift = drift(Set.of("tags", "teams"), Set.of("tags"));

      // Assert
      assertThat(drift).containsExactly("production arms 'teams', the test profile does not");
    }

    @Test
    @DisplayName("given a profile that arms a table production does not, should name it")
    void given_profileThatArmsAnExtraTable_should_nameIt() {
      // Act
      List<String> drift = drift(Set.of("tags"), Set.of("tags", "teams"));

      // Assert
      assertThat(drift).containsExactly("the test profile arms 'teams', production does not");
    }
  }
}
