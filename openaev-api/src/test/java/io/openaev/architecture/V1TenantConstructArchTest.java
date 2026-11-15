package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import io.openaev.architecture.V1TenantConstructCatalog.Site;
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
 * Freezes the multi-tenancy v1 constructs in production code while they are removed: the calls to
 * {@code TenantContext} and to {@code AmbientTenantBridge}, the {@code @EntityListeners} that name
 * a tenant listener, and the {@code #tenantContext} SpEL expressions of the repository queries
 * ({@link V1TenantConstructCatalog} says what it finds and what it cannot see). The v1 layer reads
 * a per-thread tenant that falls back to the default tenant when nobody set it; v2 scopes every
 * read and write from an explicit scope, so a new v1 site is a site the v2 removal has to come back
 * for.
 *
 * <p>Every site today is a line of {@code v1-tenant-construct-baseline.txt} with its count and its
 * reason. A site absent from the file fails, a line with no site fails, a line whose count differs
 * from the code fails, and the number of lines is pinned by {@link #BASELINE_SIZE} by equality: a
 * removed site lowers the constant in the same change, so the file only shrinks. A new site uses
 * the v2 scope it needs ({@code TxCtx}, the background primitive) instead of a line here.
 *
 * <p>What the checks cannot tell, so a reviewer reads the diff of the file: a line removed and a
 * line added in the same change keep the size, and a renumbered anonymous class ({@code Outer$1})
 * shows as a stale line plus a new site.
 */
@DisplayName("Production code adds no multi-tenancy v1 construct outside its shrinking baseline")
class V1TenantConstructArchTest {

  /** How many lines the baseline holds. Lower it with the line you remove, never raise it. */
  static final int BASELINE_SIZE = 242;

  @TestFactory
  @DisplayName("given the production classes, should pass every check of the guard")
  Stream<DynamicTest> given_productionClasses_should_passEveryCheckOfTheGuard() {
    Scan scan =
        Scan.of(LinkedIdResolutionCatalog.productionClasses(), V1TenantConstructBaseline.load());
    return scan.checks(BASELINE_SIZE).stream()
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
   * The sites of a set of classes and what the guard reports against a baseline. The test above
   * runs the checks over the production classes; {@link V1TenantConstructRulesTest} runs them over
   * fixtures.
   */
  record Scan(Map<Site, Integer> sites, List<V1TenantConstructBaseline.Entry> baseline) {

    /** One check of the guard: what it requires, what to do when it fails, what it finds. */
    record Check(String name, String guidance, Supplier<List<String>> found) {}

    static Scan of(JavaClasses classes, List<V1TenantConstructBaseline.Entry> baseline) {
      return new Scan(V1TenantConstructCatalog.sites(classes), baseline);
    }

    List<Check> checks(int baselineSize) {
      return List.of(
          new Check(
              "should find every site declared in the baseline",
              "A new v1 construct reads or sets the per-thread tenant, which falls back to the"
                  + " default tenant. Take the tenant from the request TxCtx, or run background"
                  + " work through the scoped transaction primitive, instead of adding a line to "
                  + V1TenantConstructBaseline.RESOURCE,
              this::undeclared),
          new Check(
              "should find a site for every baseline line",
              "These lines of "
                  + V1TenantConstructBaseline.RESOURCE
                  + " describe a site that is gone: remove them and lower BASELINE_SIZE",
              this::stale),
          new Check(
              "should find the pinned count on every site",
              "The count of a listed site changed. A call added there is a new v1 construct; a"
                  + " call removed lowers the number in "
                  + V1TenantConstructBaseline.RESOURCE,
              this::miscounted),
          new Check(
              "should find a reason on every line",
              "every line of " + V1TenantConstructBaseline.RESOURCE,
              () -> V1TenantConstructBaseline.malformed(baseline)),
          new Check(
              "should find exactly the pinned number of lines",
              V1TenantConstructBaseline.RESOURCE
                  + " only shrinks: lower BASELINE_SIZE with the line you remove",
              () -> sized(baselineSize)));
    }

    /** Each site the baseline does not declare, written as the line it would need. */
    List<String> undeclared() {
      Set<Site> declared = new HashSet<>();
      baseline.forEach(entry -> declared.add(entry.site()));
      return sites.entrySet().stream()
          .filter(site -> !declared.contains(site.getKey()))
          .map(site -> site.getKey() + " | " + site.getValue() + " | <why>")
          .toList();
    }

    /** Each baseline line whose site is gone. */
    List<String> stale() {
      return baseline.stream()
          .filter(entry -> !sites.containsKey(entry.site()))
          .map(entry -> "line " + entry.line() + " '" + entry.site() + "'")
          .toList();
    }

    /** Each baseline line whose count differs from what the scan finds. */
    List<String> miscounted() {
      return baseline.stream()
          .filter(entry -> sites.containsKey(entry.site()) && entry.countValue() > 0)
          .filter(entry -> entry.countValue() != sites.get(entry.site()))
          .map(
              entry ->
                  "line "
                      + entry.line()
                      + " '"
                      + entry.site()
                      + "' says "
                      + entry.countValue()
                      + ", the scan finds "
                      + sites.get(entry.site()))
          .toList();
    }

    List<String> sized(int baselineSize) {
      return baseline.size() == baselineSize
          ? List.of()
          : List.of(
              "the baseline holds "
                  + baseline.size()
                  + " lines, not BASELINE_SIZE "
                  + baselineSize
                  + ". Removed a line? Lower the constant. Added one? A new site does not join"
                  + " the baseline.");
    }
  }
}
