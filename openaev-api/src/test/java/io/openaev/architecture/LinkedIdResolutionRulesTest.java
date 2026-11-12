package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.architecture.LinkedIdResolutionArchTest.Scan;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsADeclaredFindAllByIdIn;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsAnInheritedMethodOnTheChild;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsFindAllById;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsFindAllByIdTwice;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsFindById;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsGetReferenceById;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsInALambda;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsOnABareRepository;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsOnAnInactiveTable;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsTheScopedOverload;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsTheUnscopedOverload;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsThroughAMethodReference;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsThroughAnUnboundMethodReference;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsTwoRepositoriesThroughMethodReferences;
import io.openaev.architecture.fixture.LinkedIdResolutionFixtures.CallsWithATenant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the linked-id guard finds every shape of call, leaves the tenant-scoped ones alone, and
 * rejects a baseline that differs from the code. {@link LinkedIdResolutionArchTest} runs the same
 * checks over the production classes.
 */
@DisplayName("Linked id guard: it finds each shape of call and reads the baseline strictly")
class LinkedIdResolutionRulesTest {

  private static final String FIXTURES = LinkedIdResolutionFixtures.class.getName() + "$";
  private static final String ANCHOR = FIXTURES + "LinkedItemRepository";
  private static final String GOOD_REASON = "resolves the ids a request body names to link rows";

  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter()
          .importClasses(
              List.<Class<?>>of(LinkedIdResolutionFixtures.class.getDeclaredClasses())
                  .toArray(Class<?>[]::new));

  private static final Set<String> ACTIVE = Set.of(LinkedIdResolutionFixtures.ACTIVE_TABLE);

  private static final JavaClasses WITHOUT_UNMAPPED =
      new ClassFileImporter()
          .importClasses(
              java.util.Arrays.stream(LinkedIdResolutionFixtures.class.getDeclaredClasses())
                  .filter(type -> !type.getSimpleName().equals("UnmappedItemRepository"))
                  .toArray(Class<?>[]::new));

  private static String site(Class<?> caller, String method, String repository) {
    return caller.getName() + "#" + method + " -> " + repository;
  }

  private static Scan scanWith(List<LinkedIdResolutionBaseline.Entry> baseline) {
    return Scan.of(
        FIXTURE_CLASSES,
        baseline,
        Set.of(LinkedIdResolutionFixtures.ACTIVE_TABLE),
        ANCHOR,
        LinkedIdResolutionFixtures.ACTIVE_TABLE);
  }

  private static List<LinkedIdResolutionBaseline.Entry> baselineOf(Scan scan) {
    List<String> lines = new ArrayList<>();
    scan.sites()
        .forEach((site, calls) -> lines.add(site + " | link | " + calls + " | " + GOOD_REASON));
    return LinkedIdResolutionBaseline.parse(lines);
  }

  /** The baseline of the scan, with the call count of one site changed to {@code calls}. */
  private static List<LinkedIdResolutionBaseline.Entry> baselineWithCalls(
      Scan scan, String site, int calls) {
    List<String> lines = new ArrayList<>();
    scan.sites()
        .forEach(
            (each, found) ->
                lines.add(
                    each
                        + " | link | "
                        + (each.equals(site) ? calls : found)
                        + " | "
                        + GOOD_REASON));
    return LinkedIdResolutionBaseline.parse(lines);
  }

  private static List<String> found(Scan scan, String checkName, int link, int other) {
    return scan.checks(link, other).stream()
        .filter(check -> check.name().equals(checkName))
        .findFirst()
        .orElseThrow()
        .found()
        .get();
  }

  private static final String NEW_SITE_CHECK = "should find every site declared in the baseline";
  private static final String STALE_CHECK = "should find a site for every baseline line";
  private static final String CALLS_CHECK = "should find the pinned number of calls on every site";
  private static final String FORMAT_CHECK = "should find a classified reason on every line";
  private static final String COUNT_CHECK =
      "should find exactly the pinned numbers of link and other lines";
  private static final String UNRESOLVED_CHECK = "should resolve the table of every repository";
  private static final String ANCHOR_CHECK =
      "should resolve the repository it anchors on to its table";

  @Nested
  @DisplayName("The scan")
  class TheScan {

    @Test
    @DisplayName("given each shape of unscoped resolution, should find one site for it")
    void given_eachShapeOfUnscopedResolution_should_findOneSiteForIt() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      Set<String> sites = scan.sites().keySet();

      // Assert
      assertThat(sites)
          .containsExactlyInAnyOrder(
              site(CallsFindAllById.class, "link", "LinkedItemRepository"),
              site(CallsFindAllByIdTwice.class, "link", "LinkedItemRepository"),
              site(CallsInALambda.class, "link", "LinkedItemRepository"),
              site(CallsThroughAMethodReference.class, "link", "LinkedItemRepository"),
              site(CallsThroughAnUnboundMethodReference.class, "link", "LinkedItemRepository"),
              site(CallsAnInheritedMethodOnTheChild.class, "link", "LinkedItemChildRepository"),
              site(CallsADeclaredFindAllByIdIn.class, "link", "LinkedItemRepository"),
              site(CallsGetReferenceById.class, "link", "LinkedItemRepository"),
              site(CallsOnABareRepository.class, "link", "BareLinkedItemRepository"),
              site(
                  CallsTwoRepositoriesThroughMethodReferences.class,
                  "link",
                  "LinkedItemRepository"),
              site(
                  CallsTwoRepositoriesThroughMethodReferences.class,
                  "link",
                  "SiblingLinkedItemRepository"),
              site(CallsTheUnscopedOverload.class, "link", "OverloadedItemRepository"));
    }

    @Test
    @DisplayName(
        "given two repositories sharing an inherited method reference, should count one each")
    void given_twoRepositoriesSharingAnInheritedMethodReference_should_countOneEach() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      Integer first =
          scan.sites()
              .get(
                  site(
                      CallsTwoRepositoriesThroughMethodReferences.class,
                      "link",
                      "LinkedItemRepository"));
      Integer second =
          scan.sites()
              .get(
                  site(
                      CallsTwoRepositoriesThroughMethodReferences.class,
                      "link",
                      "SiblingLinkedItemRepository"));

      // Assert
      assertThat(first).isEqualTo(1);
      assertThat(second).isEqualTo(1);
    }

    @Test
    @DisplayName("given an overload with a tenant next to one without, should find only the second")
    void given_overloadWithATenantNextToOneWithout_should_findOnlyTheSecond() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      Set<String> sites = scan.sites().keySet();

      // Assert
      assertThat(sites)
          .contains(site(CallsTheUnscopedOverload.class, "link", "OverloadedItemRepository"))
          .noneMatch(site -> site.startsWith(CallsTheScopedOverload.class.getName() + "#"));
    }

    @Test
    @DisplayName("given several calls in one method, should count them as one site")
    void given_severalCallsInOneMethod_should_countThemAsOneSite() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      Integer calls =
          scan.sites().get(site(CallsFindAllByIdTwice.class, "link", "LinkedItemRepository"));

      // Assert
      assertThat(calls).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "given calls with a tenant, a call on an inactive table or findById, should find none")
    void given_callsWithATenantOrOnAnInactiveTableOrFindById_should_findNone() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      Set<String> sites = scan.sites().keySet();

      // Assert
      assertThat(sites)
          .noneMatch(
              site ->
                  site.startsWith(CallsWithATenant.class.getName() + "#")
                      || site.startsWith(CallsOnAnInactiveTable.class.getName() + "#")
                      || site.startsWith(CallsFindById.class.getName() + "#"));
    }

    @Test
    @DisplayName("given the same call on a table that becomes active, should find it")
    void given_sameCallOnATableThatBecomesActive_should_findIt() {
      // Arrange
      Scan scan =
          Scan.of(
              FIXTURE_CLASSES,
              List.of(),
              Set.of(
                  LinkedIdResolutionFixtures.ACTIVE_TABLE,
                  LinkedIdResolutionFixtures.INACTIVE_TABLE),
              ANCHOR,
              LinkedIdResolutionFixtures.ACTIVE_TABLE);

      // Act
      Set<String> sites = scan.sites().keySet();

      // Assert
      assertThat(sites)
          .contains(site(CallsOnAnInactiveTable.class, "link", "InactiveItemRepository"));
    }

    @Test
    @DisplayName("given a repository with no table, should report it and not skip it")
    void given_repositoryWithNoTable_should_reportItAndNotSkipIt() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      List<String> unresolved = found(scan, UNRESOLVED_CHECK, 0, 0);

      // Assert
      assertThat(unresolved).hasSize(1);
      assertThat(unresolved.getFirst()).startsWith(FIXTURES + "UnmappedItemRepository");
    }

    @Test
    @DisplayName("given a generic base and a bare Repository, should read only the second")
    void given_genericBaseAndBareRepository_should_readOnlyTheSecond() {
      // Arrange
      Scan scan = scanWith(List.of());

      // Act
      var tables = scan.repositories().tables();

      // Assert
      assertThat(tables)
          .containsEntry(
              FIXTURES + "BareLinkedItemRepository", LinkedIdResolutionFixtures.ACTIVE_TABLE)
          .containsEntry(
              FIXTURES + "LinkedItemChildRepository", LinkedIdResolutionFixtures.ACTIVE_TABLE)
          .doesNotContainKey(FIXTURES + "GenericBaseRepository");
    }
  }

  @Nested
  @DisplayName("The checks")
  class TheChecks {

    @Test
    @DisplayName("given a baseline that lists every site, should pass every check")
    void given_baselineListingEverySite_should_passEveryCheck() {
      // Arrange
      Scan clean =
          Scan.of(
              WITHOUT_UNMAPPED, List.of(), ACTIVE, ANCHOR, LinkedIdResolutionFixtures.ACTIVE_TABLE);
      Scan scan =
          Scan.of(
              WITHOUT_UNMAPPED,
              baselineOf(clean),
              ACTIVE,
              ANCHOR,
              LinkedIdResolutionFixtures.ACTIVE_TABLE);

      // Act
      List<String> findings = new ArrayList<>();
      scan.checks(scan.sites().size(), 0).forEach(check -> findings.addAll(check.found().get()));

      // Assert
      assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("given a site absent from the baseline, should name it as the line it needs")
    void given_siteAbsentFromTheBaseline_should_nameItAsTheLineItNeeds() {
      // Arrange
      List<LinkedIdResolutionBaseline.Entry> baseline = baselineOf(scanWith(List.of()));
      String added = site(CallsGetReferenceById.class, "link", "LinkedItemRepository");
      baseline = baseline.stream().filter(entry -> !entry.site().equals(added)).toList();

      // Act
      List<String> undeclared = found(scanWith(baseline), NEW_SITE_CHECK, 0, 0);

      // Assert
      assertThat(undeclared).containsExactly(added + " | <link or other> | 1 | <why>");
    }

    @Test
    @DisplayName("given a baseline line with no site, should report it as stale")
    void given_baselineLineWithNoSite_should_reportItAsStale() {
      // Arrange
      List<LinkedIdResolutionBaseline.Entry> baseline =
          new ArrayList<>(baselineOf(scanWith(List.of())));
      baseline.addAll(
          LinkedIdResolutionBaseline.parse(
              List.of("io.openaev.Gone#removed -> TagRepository | other | 1 | " + GOOD_REASON)));

      // Act
      List<String> stale = found(scanWith(baseline), STALE_CHECK, 0, 0);

      // Assert
      assertThat(stale).hasSize(1);
      assertThat(stale.getFirst()).contains("io.openaev.Gone#removed -> TagRepository");
    }

    @Test
    @DisplayName("given a duplicate line, should report it")
    void given_duplicateLine_should_reportIt() {
      // Arrange
      String line = "io.openaev.A#b -> TagRepository | link | 1 | " + GOOD_REASON;
      var baseline = LinkedIdResolutionBaseline.parse(List.of(line, line));

      // Act
      List<String> problems = LinkedIdResolutionBaseline.malformed(baseline);

      // Assert
      assertThat(problems).hasSize(1);
      assertThat(problems.getFirst()).contains("duplicates line 1");
    }

    @Test
    @DisplayName(
        "given an unknown kind, a missing or invalid call count, a short reason or a malformed"
            + " site, should report each")
    void given_unknownKindBadCallCountShortReasonOrMalformedSite_should_reportEach() {
      // Arrange
      var baseline =
          LinkedIdResolutionBaseline.parse(
              List.of(
                  "io.openaev.A#b -> TagRepository | maybe | 1 | " + GOOD_REASON,
                  "io.openaev.C#d -> TagRepository | link | 1 | too short a reason",
                  "io.openaev.E#f -> TagRepository | other | 1 |",
                  "not a site | link | 1 | " + GOOD_REASON,
                  "io.openaev.G#h -> TagRepository | link | " + GOOD_REASON,
                  "io.openaev.I#j -> TagRepository | link | 0 | " + GOOD_REASON,
                  "io.openaev.K#l -> TagRepository | link | two | " + GOOD_REASON));

      // Act
      List<String> problems = LinkedIdResolutionBaseline.malformed(baseline);

      // Assert
      assertThat(problems).hasSize(7);
      assertThat(problems.get(0)).contains("must be one of");
      assertThat(problems.get(1)).contains("at least 6 words");
      assertThat(problems.get(2)).contains("at least 6 words");
      assertThat(problems.get(3)).contains("fully.qualified.Class#method -> Repository");
      assertThat(problems.subList(4, 7))
          .allSatisfy(problem -> assertThat(problem).contains("number of calls"));
    }

    @Test
    @DisplayName("given a malformed line, should fail the check that reads every line")
    void given_malformedLine_should_failTheCheckThatReadsEveryLine() {
      // Arrange
      List<LinkedIdResolutionBaseline.Entry> baseline =
          new ArrayList<>(baselineOf(scanWith(List.of())));
      baseline.addAll(
          LinkedIdResolutionBaseline.parse(
              List.of("io.openaev.Gone#removed -> TagRepository | maybe | 1 | " + GOOD_REASON)));

      // Act
      List<String> problems = found(scanWith(baseline), FORMAT_CHECK, 0, 0);

      // Assert
      assertThat(problems).singleElement().asString().contains("must be one of");
    }

    @Test
    @DisplayName("given one more call in a listed method than its line says, should report it")
    void given_oneMoreCallInAListedMethod_should_reportIt() {
      // Arrange
      String listed = site(CallsFindAllByIdTwice.class, "link", "LinkedItemRepository");
      Scan scan = scanWith(baselineWithCalls(scanWith(List.of()), listed, 1));

      // Act
      List<String> problems = found(scan, CALLS_CHECK, 0, 0);

      // Assert
      assertThat(problems)
          .singleElement()
          .asString()
          .contains(listed + "' says 1, the scan finds 2");
    }

    @Test
    @DisplayName("given one fewer call in a listed method than its line says, should report it")
    void given_oneFewerCallInAListedMethod_should_reportIt() {
      // Arrange
      String listed = site(CallsFindAllByIdTwice.class, "link", "LinkedItemRepository");
      Scan scan = scanWith(baselineWithCalls(scanWith(List.of()), listed, 3));

      // Act
      List<String> problems = found(scan, CALLS_CHECK, 0, 0);

      // Assert
      assertThat(problems)
          .singleElement()
          .asString()
          .contains(listed + "' says 3, the scan finds 2");
    }

    @Test
    @DisplayName("given a baseline whose call counts match the code, should report none")
    void given_baselineWhoseCallCountsMatchTheCode_should_reportNone() {
      // Arrange
      Scan scan = scanWith(baselineOf(scanWith(List.of())));

      // Act
      List<String> problems = found(scan, CALLS_CHECK, 0, 0);

      // Assert
      assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("given a link count off by one either way, should report it")
    void given_linkCountOffByOneEitherWay_should_reportIt() {
      // Arrange
      Scan scan = scanWith(baselineOf(scanWith(List.of())));
      int links = scan.sites().size();

      // Act
      List<String> tooLow = found(scan, COUNT_CHECK, links - 1, 0);
      List<String> tooHigh = found(scan, COUNT_CHECK, links + 1, 0);

      // Assert
      assertThat(tooLow).singleElement().asString().contains("not LINK_DEBT_SIZE " + (links - 1));
      assertThat(tooHigh).singleElement().asString().contains("not LINK_DEBT_SIZE " + (links + 1));
    }

    @Test
    @DisplayName("given an other count off by one either way, should report it")
    void given_otherCountOffByOneEitherWay_should_reportIt() {
      // Arrange
      Scan scan = scanWith(baselineOf(scanWith(List.of())));
      int links = scan.sites().size();

      // Act
      List<String> tooLow = found(scan, COUNT_CHECK, links, -1);
      List<String> tooHigh = found(scan, COUNT_CHECK, links, 1);

      // Assert
      assertThat(tooLow).singleElement().asString().contains("not OTHER_SIZE -1");
      assertThat(tooHigh).singleElement().asString().contains("not OTHER_SIZE 1");
    }

    @Test
    @DisplayName("given an anchor that does not resolve to its table, should report it")
    void given_anchorThatDoesNotResolveToItsTable_should_reportIt() {
      // Arrange
      Scan scan =
          Scan.of(
              FIXTURE_CLASSES,
              List.of(),
              Set.of(LinkedIdResolutionFixtures.ACTIVE_TABLE),
              ANCHOR,
              "another_table");

      // Act
      List<String> problems = found(scan, ANCHOR_CHECK, 0, 0);

      // Assert
      assertThat(problems).singleElement().asString().contains("not another_table");
    }

    @Test
    @DisplayName("given classes with no repository, should report that nothing resolved")
    void given_classesWithNoRepository_should_reportThatNothingResolved() {
      // Arrange
      Scan scan =
          Scan.of(
              new ClassFileImporter().importClasses(CallsFindById.class),
              List.of(),
              Set.of(LinkedIdResolutionFixtures.ACTIVE_TABLE),
              ANCHOR,
              LinkedIdResolutionFixtures.ACTIVE_TABLE);

      // Act
      List<String> problems = found(scan, ANCHOR_CHECK, 0, 0);

      // Assert
      assertThat(problems).containsExactly("no repository resolved to a table");
    }
  }
}
