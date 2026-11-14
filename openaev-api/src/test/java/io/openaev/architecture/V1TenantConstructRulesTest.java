package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.architecture.V1TenantConstructArchTest.Scan;
import io.openaev.architecture.V1TenantConstructCatalog.Site;
import io.openaev.architecture.fixture.V1TenantConstructFixtures;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.CallsTheBridge;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.ChecksTheAmbientTenantInsideANestedLambda;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.ClearsTheAmbientTenantInAFinally;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.ClearsTheAmbientTenantInsideALambda;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.ReadsTheAmbientTenant;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.ReadsTheAmbientTenantTwice;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.SetsTheAmbientTenantThroughAMethodReference;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.StampedByASubclassEntity;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.StampedEntity;
import io.openaev.architecture.fixture.V1TenantConstructFixtures.StampsFromTheAmbientTenant;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the v1 construct guard finds every shape of construct, leaves the rest alone, and rejects
 * a baseline that differs from the code. {@link V1TenantConstructArchTest} runs the same checks
 * over the production classes.
 */
@DisplayName("v1 construct guard: it finds each shape of construct and reads the baseline strictly")
class V1TenantConstructRulesTest {

  private static final String GOOD_REASON = "reads the ambient tenant as the request tenant";

  private static final String NEW_SITE_CHECK = "should find every site declared in the baseline";
  private static final String STALE_CHECK = "should find a site for every baseline line";
  private static final String COUNT_CHECK = "should find the pinned count on every site";
  private static final String FORMAT_CHECK = "should find a reason on every line";
  private static final String SIZE_CHECK = "should find exactly the pinned number of lines";

  /**
   * Compiled by {@link #compiledQueryFixtures} without annotation processing: this module refuses
   * Spring Data query annotations in its own sources.
   */
  private static final String QUERY_FIXTURES =
      """
      package fixtures;
      import org.springframework.data.jpa.repository.Query;
      import org.springframework.data.repository.CrudRepository;
      public class QueryFixtures {
        public interface QueriesTheAmbientTenant {
          @Query(
              value = "SELECT i FROM Item i WHERE i.tenant.id = :#{#tenantContext.currentTenant}",
              countQuery = "SELECT count(i) FROM Item i WHERE i.tenant.id = :#{#tenantContext.currentTenant}")
          Object findScoped();

          @Query("SELECT i FROM Item i WHERE i.tenant.id = :tenantId")
          Object findWithATenant(String tenantId);
        }

        /** Overrides a generic method, so javac adds a bridge method carrying the same @Query. */
        public interface DeletesInTheAmbientTenant extends CrudRepository<Object, String> {
          @Query("DELETE FROM items WHERE id = :id AND tenant_id = :#{#tenantContext.currentTenant}")
          void deleteById(String id);
        }
      }
      """;

  private static JavaClasses compiledQueryFixtures(Path directory) throws Exception {
    Path source = directory.resolve("fixtures/QueryFixtures.java");
    Files.createDirectories(source.getParent());
    Files.writeString(source, QUERY_FIXTURES);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    ByteArrayOutputStream messages = new ByteArrayOutputStream();
    int status =
        compiler.run(
            null,
            messages,
            messages,
            "-proc:none",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            directory.toString(),
            source.toString());
    assertThat(status).as("the query fixtures compile: %s", messages).isZero();
    return new ClassFileImporter().importPath(directory);
  }

  private static JavaClasses fixtureClasses() {
    return new ClassFileImporter()
        .importClasses(V1TenantConstructFixtures.class.getDeclaredClasses());
  }

  private static Site call(Class<?> caller, String method, String construct) {
    return new Site(caller.getName() + "#" + method, construct);
  }

  private static List<String> linesOf(Map<Site, Integer> sites) {
    List<String> lines = new ArrayList<>();
    sites.forEach((site, count) -> lines.add(site + " | " + count + " | " + GOOD_REASON));
    return lines;
  }

  private static Scan scanWith(Map<Site, Integer> sites, List<String> lines) {
    return new Scan(sites, V1TenantConstructBaseline.parse(lines));
  }

  private static List<String> found(Scan scan, String checkName, int baselineSize) {
    return scan.checks(baselineSize).stream()
        .filter(check -> check.name().equals(checkName))
        .findFirst()
        .orElseThrow()
        .found()
        .get();
  }

  @Nested
  @DisplayName("The scan")
  class TheScan {

    @Test
    @DisplayName("given each shape of v1 construct, should find one site with its count")
    void given_eachShapeOfV1Construct_should_findOneSiteWithItsCount() {
      // Act
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());

      // Assert
      assertThat(sites)
          .containsExactlyInAnyOrderEntriesOf(
              Map.ofEntries(
                  Map.entry(
                      call(ReadsTheAmbientTenant.class, "read", "TenantContext.getCurrentTenant"),
                      1),
                  Map.entry(
                      call(
                          ReadsTheAmbientTenantTwice.class,
                          "readTwice",
                          "TenantContext.getCurrentTenant"),
                      2),
                  Map.entry(
                      call(
                          SetsTheAmbientTenantThroughAMethodReference.class,
                          "setter",
                          "TenantContext.setCurrentTenant"),
                      1),
                  Map.entry(
                      call(
                          ClearsTheAmbientTenantInsideALambda.class,
                          "later",
                          "TenantContext.clearCurrentTenant"),
                      1),
                  Map.entry(
                      call(
                          ChecksTheAmbientTenantInsideANestedLambda.class,
                          "nested",
                          "TenantContext.hasCurrentTenant"),
                      1),
                  Map.entry(
                      call(
                          ClearsTheAmbientTenantInAFinally.class,
                          "readThenClear",
                          "TenantContext.getCurrentTenant"),
                      1),
                  Map.entry(
                      call(
                          ClearsTheAmbientTenantInAFinally.class,
                          "readThenClear",
                          "TenantContext.clearCurrentTenant"),
                      1),
                  Map.entry(
                      call(CallsTheBridge.class, "callIn", "AmbientTenantBridge.callInTenant"), 1),
                  Map.entry(
                      call(
                          StampsFromTheAmbientTenant.class,
                          "stamp",
                          "TenantContext.getCurrentTenant"),
                      1),
                  Map.entry(
                      new Site(
                          StampedEntity.class.getName(),
                          "@EntityListeners(StampsFromTheAmbientTenant)"),
                      1),
                  Map.entry(
                      new Site(
                          StampedByASubclassEntity.class.getName(),
                          "@EntityListeners(InheritsTheAmbientStamp)"),
                      1)));
    }

    @Test
    @DisplayName("given a @Query reading the v1 tenant, should count each expression once")
    void given_aQueryReadingTheV1Tenant_should_countEachExpressionOnce(@TempDir Path directory)
        throws Exception {
      // Arrange
      JavaClasses queries = compiledQueryFixtures(directory);

      // Act
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(queries);

      // Assert
      assertThat(sites)
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  new Site(
                      "fixtures.QueryFixtures$QueriesTheAmbientTenant#findScoped",
                      "@Query(#tenantContext)"),
                  2,
                  new Site(
                      "fixtures.QueryFixtures$DeletesInTheAmbientTenant#deleteById",
                      "@Query(#tenantContext)"),
                  1));
    }

    @Test
    @DisplayName("given the two v1 classes, should not count their calls on themselves")
    void given_theTwoV1Classes_should_notCountTheirCallsOnThemselves() {
      // Arrange
      JavaClasses v1Classes =
          new ClassFileImporter().importClasses(TenantContext.class, AmbientTenantBridge.class);

      // Act
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(v1Classes);

      // Assert
      assertThat(sites.keySet())
          .isNotEmpty()
          .allSatisfy(
              site -> {
                assertThat(site.location()).startsWith(AmbientTenantBridge.class.getName());
                assertThat(site.construct()).startsWith("TenantContext.");
              });
    }
  }

  @Nested
  @DisplayName("The checks")
  class TheChecks {

    @Test
    @DisplayName("given a baseline equal to the scan, should report nothing")
    void given_aBaselineEqualToTheScan_should_reportNothing() {
      // Arrange
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());
      Scan scan = scanWith(sites, linesOf(sites));

      // Act & Assert
      assertThat(scan.checks(sites.size()))
          .allSatisfy(check -> assertThat(check.found().get()).as(check.name()).isEmpty());
    }

    @Test
    @DisplayName("given a site absent from the baseline, should name it as the line to add")
    void given_aSiteAbsentFromTheBaseline_should_nameItAsTheLineToAdd() {
      // Arrange
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());
      Site added = call(ReadsTheAmbientTenant.class, "read", "TenantContext.getCurrentTenant");
      List<String> lines =
          linesOf(sites).stream().filter(line -> !line.startsWith(added + " |")).toList();
      Scan scan = scanWith(sites, lines);

      // Act
      List<String> newSites = found(scan, NEW_SITE_CHECK, lines.size());

      // Assert
      assertThat(newSites).containsExactly(added + " | 1 | <why>");
    }

    @Test
    @DisplayName("given a baseline line with no site, should report it as stale")
    void given_aBaselineLineWithNoSite_should_reportItAsStale() {
      // Arrange
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());
      List<String> lines = new ArrayList<>(linesOf(sites));
      lines.add("io.openaev.Gone#method | TenantContext.getCurrentTenant | 1 | " + GOOD_REASON);
      Scan scan = scanWith(sites, lines);

      // Act
      List<String> stale = found(scan, STALE_CHECK, lines.size());

      // Assert
      assertThat(stale).singleElement().asString().contains("io.openaev.Gone#method");
    }

    @Test
    @DisplayName("given a line whose count differs from the code, should report both numbers")
    void given_aLineWhoseCountDiffersFromTheCode_should_reportBothNumbers() {
      // Arrange
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());
      Site twice =
          call(ReadsTheAmbientTenantTwice.class, "readTwice", "TenantContext.getCurrentTenant");
      List<String> lines =
          linesOf(sites).stream()
              .map(line -> line.startsWith(twice + " |") ? line.replace("| 2 |", "| 1 |") : line)
              .toList();
      Scan scan = scanWith(sites, lines);

      // Act
      List<String> miscounted = found(scan, COUNT_CHECK, lines.size());

      // Assert
      assertThat(miscounted).singleElement().asString().contains("says 1, the scan finds 2");
    }

    @Test
    @DisplayName("given a size constant that differs from the lines, should report it")
    void given_aSizeConstantThatDiffersFromTheLines_should_reportIt() {
      // Arrange
      Map<Site, Integer> sites = V1TenantConstructCatalog.sites(fixtureClasses());
      Scan scan = scanWith(sites, linesOf(sites));

      // Act
      List<String> lowered = found(scan, SIZE_CHECK, sites.size() - 1);
      List<String> raised = found(scan, SIZE_CHECK, sites.size() + 1);

      // Assert
      assertThat(lowered).singleElement().asString().contains("not BASELINE_SIZE");
      assertThat(raised).singleElement().asString().contains("not BASELINE_SIZE");
    }

    @Test
    @DisplayName("given lines a reader cannot trust, should report each of them")
    void given_linesAReaderCannotTrust_should_reportEachOfThem() {
      // Arrange
      String site = "io.openaev.Some#method";
      List<String> lines =
          List.of(
              site + " | TenantContext.getCurrentTenant | 1 | too short",
              site + " | TenantContext.getCurrentTenant | 1 | " + GOOD_REASON,
              "io.openaev.Other#method | TenantFilter.enable | 1 | " + GOOD_REASON,
              "io.openaev.Third#method | TenantContext.setCurrentTenant | 0 | " + GOOD_REASON,
              "not a class | TenantContext.setCurrentTenant | 1 | " + GOOD_REASON);
      Scan scan = scanWith(Map.of(), lines);

      // Act
      List<String> malformed = found(scan, FORMAT_CHECK, lines.size());

      // Assert
      assertThat(malformed)
          .hasSize(5)
          .anySatisfy(problem -> assertThat(problem).contains("at least 6 words"))
          .anySatisfy(problem -> assertThat(problem).contains("duplicates line 1"))
          .anySatisfy(problem -> assertThat(problem).contains("v1 construct"))
          .anySatisfy(problem -> assertThat(problem).contains("integer of at least 1"))
          .anySatisfy(problem -> assertThat(problem).contains("fully.qualified.Class#method"));
    }
  }
}
