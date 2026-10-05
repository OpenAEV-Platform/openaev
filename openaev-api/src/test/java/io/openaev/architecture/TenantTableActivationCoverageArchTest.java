package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.config.TenantTables;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Inverts the activation guard. {@code openaev.tenant.active-tables} is an allowlist, so a table is
 * isolated only because someone put its name in it. {@code
 * TenantActiveTableAccessArchTest#every_active_table_is_guarded} checks the tables that
 * <em>are</em> in that list and is blind to one that was never listed, which is exactly how a new
 * {@code TenantBase} entity ships unisolated with nothing failing.
 *
 * <p>This test asserts the other direction, as a three-way rule. Every strict tenant table the
 * platform knows about is either (1) in {@code openaev.tenant.active-tables}, or (2) self-isolated
 * with a written reason ({@code TenantTables.SELF_ISOLATED_TABLES}, excluded upstream by {@code
 * restrictTo("*")}), or (3) listed in {@code tenant-tables-not-yet-activated.txt} with an issue and
 * a reason. A table in none of the three fails the build.
 *
 * <p><b>The "not yet" list is the migration burn-down.</b> It only ever shrinks: an entry whose
 * table has become active fails this test, so activating a table forces its line out in the same
 * change. When that file holds no entries the rule reads "every strict tenant table is active",
 * which is the end state #8201 describes, and this test becomes the assertion that it stays that
 * way.
 *
 * <p>The table universe is the one production activates under {@code *}: {@code
 * BackgroundEntrypointTenantScopeArchTest#wildcardActivatedTables()}, which is the entity model
 * (the {@code TenantBase}, {@code DualScopeBase} and {@code TenantIdBase} markers) unioned with the
 * checked-in inventory of strict tables no entity marks, and kept identical to the live schema by
 * {@code TenantFilteringConfigTest#backgroundGuardWildcardMatchesProductionSchema}. Deriving the
 * universe there rather than restating it is what makes this guard see a table nobody mentioned.
 */
@DisplayName("Every strict tenant table is active, self-isolated, or deferred with a reason")
class TenantTableActivationCoverageArchTest {

  private static final String NOT_YET_RESOURCE = "/tenant-tables-not-yet-activated.txt";

  /** {@code <table> | #<issue> | <reason>}. */
  private static final Pattern ISSUE = Pattern.compile("#\\d+");

  private static final Pattern TABLE = Pattern.compile("[a-z0-9_]+");

  /**
   * A reason has to say something. The bound is deliberately low: it rejects a placeholder ("tbd",
   * "later") without pretending to judge prose.
   */
  private static final int MIN_REASON_LENGTH = 20;

  /**
   * One deferral line. {@code table} is normalised for comparison against the derived tables;
   * {@code rawTable} keeps the spelling the file carries, because that is what the format check has
   * to judge.
   */
  private record Deferral(String rawTable, String table, String issue, String reason, int line) {}

  @Nested
  @DisplayName("The three-way coverage rule")
  class CoverageRule {

    @Test
    @DisplayName("every strict tenant table is active or deferred with a reason")
    void given_everyStrictTenantTable_should_beActiveOrDeferred() {
      // Arrange
      Set<String> universe = universe();
      Set<String> active = activeTables();
      Set<String> deferred = deferrals().stream().map(Deferral::table).collect(Collectors.toSet());

      // Act
      Set<String> unaccounted = new TreeSet<>(universe);
      unaccounted.removeAll(active);
      unaccounted.removeAll(deferred);

      // Assert
      assertTrue(
          unaccounted.isEmpty(),
          () ->
              unaccounted.size()
                  + " strict tenant table(s) are neither isolated nor deferred on purpose, so they"
                  + " carry no tenant isolation and nothing says why: "
                  + unaccounted
                  + ". Either add the table to openaev.tenant.active-tables (see the"
                  + " activate-tenant-table skill), or add a line to"
                  + " src/test/resources"
                  + NOT_YET_RESOURCE
                  + " naming the issue that tracks it and what blocks it. Putting it in"
                  + " TenantTables.SELF_ISOLATED_TABLES is not an option: that list is for tables"
                  + " that do not rely on the statement inspector at all.");
    }

    @Test
    @DisplayName("the universe is the whole model, not an empty set that passes trivially")
    void given_theDerivedUniverse_should_coverEveryKnownTenantTable() {
      // Arrange + Act
      Set<String> universe = universe();

      // Assert: a derivation that silently returned nothing would make the rule above vacuous.
      // The platform has well over fifty strict tenant tables; the bound only has to be far enough
      // below that to never need touching, and far enough above zero to catch a broken scan.
      assertTrue(
          universe.size() > 40, "expected the derived strict tenant tables, got: " + universe);
      assertTrue(universe.contains("documents"), "an active table must be in the universe");
      assertTrue(universe.contains("injects"), "a deferred table must be in the universe");
    }

    @Test
    @DisplayName("the terminal '*' allowlist reads as every strict table being active")
    void given_theWildcardAllowlist_should_readAsEveryTableActive() {
      // Arrange
      Set<String> wildcardActivates = Set.of("documents", "injects");

      // Act
      Set<String> wildcard = expandWildcard(Set.of("*"), () -> wildcardActivates);
      Set<String> explicit = expandWildcard(Set.of("Documents", " injects "), () -> Set.of());

      // Assert: the day the shipped property becomes '*' this guard must still see every table as
      // active, and the burn-down must then be empty for the staleness check to pass.
      assertEquals(wildcardActivates, wildcard);
      assertEquals(Set.of("documents", "injects"), explicit, "names are trimmed and lowercased");
    }

    @Test
    @DisplayName("a self-isolated table is in neither list, so the third branch stays deliberate")
    void given_aSelfIsolatedTable_should_beInNeitherList() {
      // Arrange
      Set<String> active = activeTables();
      Set<String> deferred = deferrals().stream().map(Deferral::table).collect(Collectors.toSet());

      // Act + Assert: TenantTables.restrictTo accepts a self-isolated name in an explicit
      // allowlist, so nothing else rejects activating one that way.
      for (String table : TenantTables.selfIsolatedTables()) {
        assertTrue(
            !active.contains(table),
            table
                + " is self-isolated and must not be in openaev.tenant.active-tables: the"
                + " inspector cannot gate it, which is why it has a written reason in"
                + " TenantTables.SELF_ISOLATED_TABLES");
        assertTrue(
            !deferred.contains(table),
            table
                + " is self-isolated, so it is not waiting to be activated and has no place in"
                + " the burn-down list");
      }
    }
  }

  @Nested
  @DisplayName("The not-yet-activated inventory")
  class Inventory {

    @Test
    @DisplayName("every entry carries a table, an issue and a reason")
    void given_aDeferralEntry_should_carryAnIssueAndAReason() {
      // Arrange + Act
      List<Deferral> deferrals = deferrals();

      // Assert. No non-empty requirement: an empty list is the end state #8201 describes, and the
      // coverage test above is what refuses an emptied list while tables are still deferred.
      for (Deferral deferral : deferrals) {
        String where = NOT_YET_RESOURCE + ":" + deferral.line();
        assertTrue(
            TABLE.matcher(deferral.rawTable()).matches(),
            where + " table name must be lowercase snake_case: " + deferral.rawTable());
        assertTrue(
            ISSUE.matcher(deferral.issue()).matches(),
            where + " second field must be the tracking issue as #<number>: " + deferral.issue());
        assertTrue(
            deferral.reason().length() >= MIN_REASON_LENGTH,
            where + " reason must say what blocks the activation: " + deferral.reason());
      }
    }

    @Test
    @DisplayName("every entry names a real strict tenant table that is not active yet")
    void given_aDeferralEntry_should_nameAnInactiveStrictTable() {
      // Arrange
      Set<String> universe = universe();
      Set<String> active = activeTables();

      // Act
      Set<String> unknown = new TreeSet<>();
      Set<String> stale = new TreeSet<>();
      for (Deferral deferral : deferrals()) {
        if (!universe.contains(deferral.table())) {
          unknown.add(deferral.table());
        } else if (active.contains(deferral.table())) {
          stale.add(deferral.table());
        }
      }

      // Assert
      assertTrue(
          unknown.isEmpty(),
          "these entries name no strict tenant table, so they defer nothing (typo, or the table"
              + " was renamed or dropped): "
              + unknown);
      assertTrue(
          stale.isEmpty(),
          "these tables are active, so their deferral has been served and the lines must go; the"
              + " burn-down only shrinks: "
              + stale);
    }

    @Test
    @DisplayName("no table is listed twice")
    void given_theInventory_should_listEachTableOnce() {
      // Arrange + Act
      List<String> tables = deferrals().stream().map(Deferral::table).toList();

      // Assert
      assertEquals(
          Set.copyOf(tables).size(),
          tables.size(),
          "a table is deferred twice, with two reasons that can disagree: " + tables);
    }
  }

  /**
   * The strict tenant tables production gates under {@code *}: the terminal state of the rollout,
   * and so the set this guard holds to account.
   */
  private static Set<String> universe() {
    return BackgroundEntrypointTenantScopeArchTest.wildcardActivatedTables();
  }

  /**
   * The allowlist {@code main} ships. Deliberately the checked-in file and not {@code
   * BackgroundEntrypointTenantScopeArchTest#effectiveActiveTables()}: this guard judges the shipped
   * configuration, so the nightly shadow runs, which arm their own list through a system property,
   * must not change its verdict. Reading the effective list instead made the burn-down read as
   * entirely stale under {@code *}, because a rehearsal that activates every table has served no
   * deferral.
   */
  private static Set<String> activeTables() {
    return expandWildcard(
        BackgroundEntrypointTenantScopeArchTest.activeTablesFromFile(),
        TenantTableActivationCoverageArchTest::universe);
  }

  /**
   * Normalises an allowlist, expanding the terminal {@code *} to everything it activates. Pure, so
   * both arms are driven by a test rather than by the configuration the build happens to carry: the
   * day the shipped property becomes {@code *} this guard must still read every table as active,
   * and the burn-down must be empty for it to pass, which is the end state #8201 asks for.
   */
  static Set<String> expandWildcard(Set<String> allowlist, Supplier<Set<String>> wildcardTables) {
    if (allowlist.stream().anyMatch(table -> TenantTables.ALL_STRICT.equals(table.strip()))) {
      return wildcardTables.get();
    }
    return allowlist.stream()
        .map(table -> table.strip().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Parses the burn-down inventory, keeping the line number so a failure points at the file. */
  private static List<Deferral> deferrals() {
    List<Deferral> deferrals = new ArrayList<>();
    try (InputStream in =
        TenantTableActivationCoverageArchTest.class.getResourceAsStream(NOT_YET_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(NOT_YET_RESOURCE + " is missing from the classpath");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String raw;
        int lineNumber = 0;
        while ((raw = reader.readLine()) != null) {
          lineNumber++;
          String line = raw.strip();
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          String[] fields = line.split("\\|", -1);
          if (fields.length != 3) {
            throw new IllegalStateException(
                NOT_YET_RESOURCE
                    + ":"
                    + lineNumber
                    + " must read '<table> | #<issue> | <reason>', got: "
                    + line);
          }
          deferrals.add(
              new Deferral(
                  fields[0].strip(),
                  fields[0].strip().toLowerCase(Locale.ROOT),
                  fields[1].strip(),
                  fields[2].strip(),
                  lineNumber));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return deferrals;
  }
}
