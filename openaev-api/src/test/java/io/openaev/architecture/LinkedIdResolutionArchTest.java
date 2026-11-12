package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import io.openaev.architecture.LinkedIdResolutionCatalog.Repositories;
import io.openaev.config.ProductionActiveTables;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Fails when production code resolves ids on the repository of an active tenant table without
 * naming a tenant: {@code findAllById}, {@code getReferenceById}, or a declared {@code
 * findAllByIdIn*} method with no {@code Tenant} in its name and no {@code tenantId} parameter, as a
 * call or as a method reference. Such a read returns the rows of every tenant of the request scope,
 * so ids named in a request body link rows of another tenant to the entity written. The ids resolve
 * with {@code findAllByIdInTenant(ids, tenantId)} and the tenant of the entity written; an id of
 * another tenant is then treated like an unknown id.
 *
 * <p>The sites that exist today are listed in {@code linked-id-resolution-baseline.txt}, one per
 * {@code Class#method -> Repository}, each with its number of calls and classified {@code link}
 * (resolves ids named in a request body: debt to convert) or {@code other} (its reason says what
 * the site does). A site absent from the file fails, a line with no site fails, a method holding
 * more or fewer calls than its line says fails, and the two kinds are counted, each by equality: a
 * converted site lowers {@link #LINK_DEBT_SIZE} in the same change, and the debt cannot regrow. An
 * {@code other} line may be added in review, with its reason, when an activation adds a site that
 * is not a link; a {@code link} line is never added, a newly activated table's link sites are
 * converted in the change that activates it. The classification of a line is a review call: no
 * check reads what a site does.
 *
 * <p>Outside the rule, so a reviewer reads them: {@code findById} on a body id (a path id and a
 * body id look alike in the call graph), calls through generic helpers whose receivers are typed
 * {@code CrudRepository} ({@code DatabaseHelper}), id lookups declared under other names ({@code
 * SecurityPlatformRepository.findAllByIds}, {@code AiTargetRepository.findAiTargetsByIds}, {@code
 * InjectorContractRepository.findAllByIdOrExternalIdIn}), {@code deleteAllById}, {@code
 * findAll(Specification)}, {@code entityManager.find}, and native queries (covered by the {@code
 * NativeQuery*ArchTest} guards). The calls of a lambda belong to the method that creates it.
 */
@DisplayName("Production code resolves ids on a tenant table with a tenant, or says why not")
class LinkedIdResolutionArchTest {

  /** How many lines are {@code link}. Lower it as a site is converted, never raise it. */
  static final int LINK_DEBT_SIZE = 64;

  /** How many lines are {@code other}. Raising it is a review call, with the reason in the line. */
  static final int OTHER_SIZE = 42;

  @TestFactory
  @DisplayName("given the production classes, should pass every check of the guard")
  Stream<DynamicTest> given_productionClasses_should_passEveryCheckOfTheGuard() {
    Scan scan =
        Scan.of(
            LinkedIdResolutionCatalog.productionClasses(),
            LinkedIdResolutionBaseline.load(),
            ProductionActiveTables.names(),
            "io.openaev.database.repository.TagRepository",
            "tags");
    return scan.checks(LINK_DEBT_SIZE, OTHER_SIZE).stream()
        .map(
            check ->
                DynamicTest.dynamicTest(
                    check.name(),
                    () ->
                        assertThat(check.found().get())
                            .as("%s. %s", check.name(), check.guidance())
                            .isEmpty()));
  }

  /**
   * What the guard reads from a set of classes, and what it reports against a baseline. The test
   * above runs its checks over the production classes; {@link LinkedIdResolutionRulesTest} runs
   * them over fixtures.
   */
  record Scan(
      Repositories repositories,
      Map<String, Integer> sites,
      List<LinkedIdResolutionBaseline.Entry> baseline,
      String anchorRepository,
      String anchorTable) {

    /** One check of the guard: what it requires, what to do when it fails, what it finds. */
    record Check(String name, String guidance, Supplier<List<String>> found) {}

    static Scan of(
        JavaClasses classes,
        List<LinkedIdResolutionBaseline.Entry> baseline,
        Set<String> activeTables,
        String anchorRepository,
        String anchorTable) {
      Repositories repositories = LinkedIdResolutionCatalog.repositories(classes);
      return new Scan(
          repositories,
          LinkedIdResolutionCatalog.sites(classes, repositories, activeTables),
          baseline,
          anchorRepository,
          anchorTable);
    }

    List<Check> checks(int linkDebtSize, int otherSize) {
      return List.of(
          new Check(
              "should resolve the repository it anchors on to its table",
              "a scan that resolves no repository, or not the anchor, read the wrong classes, so"
                  + " its silence means nothing",
              this::unanchored),
          new Check(
              "should resolve the table of every repository",
              "A repository whose table does not resolve is never skipped: its domain type needs"
                  + " an @Table, or the interface is a generic base with type parameters",
              repositories::unresolved),
          new Check(
              "should find every site declared in the baseline",
              "Resolve body ids with findAllByIdInTenant(ids, tenantId), as TagRepository does"
                  + " (findAllByIdInAndTenantId plus a default method refusing a null list). A site"
                  + " that does something else is declared in "
                  + LinkedIdResolutionBaseline.RESOURCE
                  + " as 'other', with its reason",
              this::undeclared),
          new Check(
              "should find a site for every baseline line",
              "These lines of "
                  + LinkedIdResolutionBaseline.RESOURCE
                  + " describe a site that is gone or converted: remove the line and lower its"
                  + " count",
              this::stale),
          new Check(
              "should find the pinned number of calls on every site",
              "The number of calls of a listed method changed. A call added there resolves ids"
                  + " without a tenant: convert it, or say in the line why it is not a link. A call"
                  + " removed: lower the number in "
                  + LinkedIdResolutionBaseline.RESOURCE,
              this::miscalled),
          new Check(
              "should find a classified reason on every line",
              "every line of " + LinkedIdResolutionBaseline.RESOURCE,
              () -> LinkedIdResolutionBaseline.malformed(baseline)),
          new Check(
              "should find exactly the pinned numbers of link and other lines",
              "the two counts of " + LinkedIdResolutionBaseline.RESOURCE,
              () -> miscounted(linkDebtSize, otherSize)));
    }

    List<String> unanchored() {
      List<String> problems = new ArrayList<>();
      if (repositories.tables().isEmpty()) {
        problems.add("no repository resolved to a table");
      } else if (!anchorTable.equals(repositories.tables().get(anchorRepository))) {
        problems.add(
            anchorRepository
                + " resolves to "
                + repositories.tables().get(anchorRepository)
                + ", not "
                + anchorTable);
      }
      return problems;
    }

    /** Each site the baseline does not declare, written as the line it would need. */
    List<String> undeclared() {
      Set<String> declared = new HashSet<>();
      baseline.forEach(entry -> declared.add(entry.site()));
      return sites.entrySet().stream()
          .filter(site -> !declared.contains(site.getKey()))
          .map(site -> site.getKey() + " | <link or other> | " + site.getValue() + " | <why>")
          .toList();
    }

    /** Each baseline line whose call count differs from the number of calls the scan finds. */
    List<String> miscalled() {
      return baseline.stream()
          .filter(entry -> sites.containsKey(entry.site()) && entry.callCount() > 0)
          .filter(entry -> entry.callCount() != sites.get(entry.site()))
          .map(
              entry ->
                  "line "
                      + entry.line()
                      + " '"
                      + entry.site()
                      + "' says "
                      + entry.callCount()
                      + ", the scan finds "
                      + sites.get(entry.site()))
          .toList();
    }

    /** Each baseline line whose site is gone. */
    List<String> stale() {
      return baseline.stream()
          .filter(entry -> !sites.containsKey(entry.site()))
          .map(entry -> "line " + entry.line() + " '" + entry.site() + "'")
          .toList();
    }

    List<String> miscounted(int linkDebtSize, int otherSize) {
      long link = LinkedIdResolutionBaseline.count(baseline, LinkedIdResolutionBaseline.LINK);
      long other = LinkedIdResolutionBaseline.count(baseline, LinkedIdResolutionBaseline.OTHER);
      List<String> miscounted = new ArrayList<>();
      if (link != linkDebtSize) {
        miscounted.add(
            "The link lines number "
                + link
                + ", not LINK_DEBT_SIZE "
                + linkDebtSize
                + ". Converted a site? Lower the constant. A new site resolves with"
                + " findAllByIdInTenant and never joins the debt.");
      }
      if (other != otherSize) {
        miscounted.add(
            "The other lines number "
                + other
                + ", not OTHER_SIZE "
                + otherSize
                + ". Removed one? Lower the constant. Added one? That is the review call.");
      }
      return miscounted;
    }
  }
}
