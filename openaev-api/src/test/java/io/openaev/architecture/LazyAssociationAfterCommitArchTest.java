package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.architecture.LazyAssociationCatalog.Association;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Scenario;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Fails when a new HTTP response serialises a lazy association into a tenant-active table.
 *
 * <p>The tenant scope is transaction-local and {@code open-in-view} keeps the session open past the
 * commit, so Jackson loads such an association with no scope and writes an empty collection where
 * rows exist. An update form filled from that response then posts the empty list back and the links
 * are deleted.
 *
 * <p>A site is an endpoint whose declared response reaches an entity carrying an association that
 * is lazy, serialised by a serializer that loads it, and stored in or joined through a table the
 * production activation list gates (see {@link LazyAssociationCatalog} for each rule). Whether a
 * fetch join or an explicit initialisation attenuates a site is not visible here; the frozen
 * baseline records, per site, what the measurement found.
 *
 * <p>The baseline only shrinks: a site that no longer exists must leave it, and its size must equal
 * {@link #BASELINE_SIZE} exactly. The count is checked for equality rather than as a ceiling, so
 * removing a line forces the number down in the same change and the baseline cannot quietly regrow
 * into the slack a ceiling would leave. A new endpoint returns a DTO, or ignores the association,
 * rather than joining the baseline.
 */
@DisplayName("No new response serialises a lazy association into a tenant-active table")
class LazyAssociationAfterCommitArchTest {

  private static final String BASELINE_RESOURCE = "/lazy-after-commit-baseline.txt";

  /**
   * The only verdicts an entry may carry. An unknown one, such as a typo or a verdict invented to
   * get a line past the guard, fails rather than passing as "has a delimiter".
   */
  private static final String[] VERDICTS = {"CONFIRMED", "ATTENUATED", "NOT ESTABLISHED"};

  /** The baseline's exact size. Lower it with every entry removed, never raise it. */
  static final int BASELINE_SIZE = 1278;

  @Nested
  @DisplayName("The inventory it is built on")
  class Inventory {

    @Test
    @DisplayName(
        "given the production controllers, when scanned, then endpoints and sites are found")
    void given_productionControllers_should_findEndpointsAndSites() {
      // -- Act --
      int endpoints = LazyAssociationCatalog.productionEndpoints().size();
      Set<String> sites = liveSites();

      // -- Assert --
      // Floors well under what the platform holds: they fail a broken scan, not a growing one.
      assertThat(endpoints).as("production endpoints found").isGreaterThan(300);
      assertThat(sites).as("live sites found").isNotEmpty();
    }

    @Test
    @DisplayName("given the challenge list, when scanned, then its lazy tags are a site")
    void given_challengeList_should_reportChallengeTags() {
      // -- Act --
      Set<String> sites = liveSites();

      // -- Assert --
      // A site measured empty in a response: the inventory must at least see that one.
      assertThat(sites).contains("ChallengeApi.challenges -> Challenge.tags");
    }

    @Test
    @DisplayName(
        "given a lazy to-one the handler initialised, when scanned, then its target's associations are sites")
    void given_lazyToOneInitialisedByHandler_should_reachItsTarget() {
      // -- Act --
      Set<String> sites = liveSites();

      // -- Assert --
      // The module writes an initialised proxy in full, so the platform's own lazy lists follow.
      assertThat(sites).contains("CollectorApi.registerCollector -> SecurityPlatform.collectors");
    }

    @Test
    @DisplayName(
        "given a DTO carrying an entity collection, when scanned, then that collection is a site")
    void given_dtoCarryingEntityCollection_should_reportIt() {
      // -- Act --
      Set<String> sites = liveSites();

      // -- Assert --
      // The finding's scenario summary holds the scenario's own tag collection, not a copy.
      assertThat(sites).contains("FindingApi.finding -> ScenarioSimple.tags");
    }

    @Test
    @DisplayName(
        "given a response documented wider than its return type, when scanned, then the documented types are reached")
    void given_responseDocumentedWiderThanReturnType_should_reachDocumentedTypes() {
      // -- Act --
      Set<String> sites = liveSites();

      // -- Assert --
      // Declared as aggregated findings, it also answers related findings, which carry a scenario.
      assertThat(sites).contains("FindingSearchApi.findings -> ScenarioSimple.tags");
    }

    @Test
    @DisplayName(
        "given a collection whose elements carry an eager association, when scanned, then that table is read")
    void given_elementsWithEagerAssociation_should_readItsTable() {
      // -- Act --
      Association exercises =
          LazyAssociationCatalog.associationsOf(Scenario.class).stream()
              .filter(association -> association.property().equals("exercises"))
              .findFirst()
              .orElseThrow();

      // -- Assert --
      // Loading the exercises joins their eager security coverage, an active table.
      assertThat(exercises.tables()).contains("exercises", "security_coverages");
    }
  }

  @Nested
  @DisplayName("The frozen baseline")
  class Baseline {

    @Test
    @DisplayName("given the live sites, when compared to the baseline, then none is new")
    void given_liveSites_should_allBeInBaseline() {
      // -- Arrange --
      Set<String> unlisted = new TreeSet<>(liveSites());

      // -- Act --
      unlisted.removeAll(baseline().keySet());

      // -- Assert --
      assertThat(unlisted)
          .as(
              "Responses that serialise a lazy association into a tenant-active table. Return a"
                  + " DTO, or ignore the association, instead of adding the site to %s:%n%s",
              BASELINE_RESOURCE, String.join(System.lineSeparator(), unlisted))
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, when compared to the live sites, then none is stale")
    void given_baseline_should_holdOnlyLiveSites() {
      // -- Arrange --
      Set<String> stale = new TreeSet<>(baseline().keySet());

      // -- Act --
      stale.removeAll(liveSites());

      // -- Assert --
      assertThat(stale)
          .as(
              """
              Baseline entries naming a response that no longer exists. Delete these lines and \
lower BASELINE_SIZE by as many:%n%s""",
              String.join(System.lineSeparator(), stale))
          .isEmpty();
    }

    @Test
    @DisplayName("given the baseline, when counted, then its size matches the frozen count exactly")
    void given_baseline_should_matchFrozenSizeExactly() {
      // -- Act & Assert --
      assertThat(baseline())
          .as(
              "Baseline size must equal BASELINE_SIZE. Removed a line? Lower the constant in the same change.")
          .hasSize(BASELINE_SIZE);
    }

    @Test
    @DisplayName(
        "given the baseline, when read, then every entry carries a known verdict and a reason")
    void given_baseline_should_carryVerdictAndReason() {
      // -- Act & Assert --
      baseline()
          .forEach(
              (site, entry) -> {
                String[] parts = entry.split("\\|", -1);
                assertThat(parts)
                    .as("\"<VERDICT> | <reason>\" for %s, got \"%s\"", site, entry)
                    .hasSize(2);
                assertThat(parts[0].trim()).as("verdict for %s", site).isIn((Object[]) VERDICTS);
                assertThat(parts[1].trim()).as("reason for %s", site).isNotEmpty();
              });
    }
  }

  private static Set<String> liveSites() {
    return LazyAssociationCatalog.siteKeys(ProductionActiveTables.names());
  }

  /** Site key to "VERDICT | reason". Lines starting with '#' are comments. */
  static Map<String, String> baseline() {
    Map<String, String> entries = new LinkedHashMap<>();
    try (InputStream in =
        LazyAssociationAfterCommitArchTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("missing " + BASELINE_RESOURCE);
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            continue;
          }
          int separator = trimmed.indexOf(" | ");
          String key = separator < 0 ? trimmed : trimmed.substring(0, separator);
          String rest = separator < 0 ? "" : trimmed.substring(separator + 3);
          entries.put(key, rest);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return entries;
  }
}
