package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.ProductionActiveTables;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * A standing guard on joins keyed on an id that is shared across tenants (#7905).
 *
 * <p>Three independent mechanisms are blind to this shape, which is why it needs a guard of its own
 * rather than one sweep. The statement inspector restricts the tables it is told about, and such a
 * join reaches a table it has no reason to restrict in that position. An entity {@code @Filter}
 * never reaches a native query at all. And a per-tenant predicate on the parent row does not
 * constrain a join whose key is not tenant-unique: the parent can be pinned to this tenant while
 * the row joined to it belongs to another.
 *
 * <p>The set of shared keys is read from {@code shared-tenant-keys.txt}, which is derived from the
 * schema and re-derived against the live schema by {@code SharedTenantKeySchemaCrossCheckTest}, so
 * it cannot rot silently while the schema moves. The queries are read from the production sources,
 * because SQL reaches the database three ways here (a native {@code @Query}, a string built in Java
 * and handed to {@code createNativeQuery}, and a constant shared between the two) and only the
 * second of those is invisible to reflection. {@link #theScanSeesEveryNativeQueryAnnotation()} pins
 * the extraction against the reflective census so a scan that stops finding queries fails instead
 * of passing.
 *
 * <p>What makes a join acceptable is not the presence of the word {@code tenant_id} nearby. Both
 * sides must be constrained to the SAME tenant, which the analyser decides by building the
 * transitive tenant-equality classes of the whole statement: aliases joined by {@code a.tenant_id =
 * b.tenant_id} share a class, and an alias pinned by {@code a.tenant_id = :param} joins the class
 * of that parameter. A key join passes only when both of its aliases land in one class. A
 * line-proximity window, which is what a first pass at this used, called a query safe because a
 * parent predicate happened to sit next to an unscoped link join.
 */
@DisplayName("No SQL join is keyed on a tenant-shared id without the tenant")
class SharedTenantKeyJoinArchTest {

  private static final String KEYS_RESOURCE = "/shared-tenant-keys.txt";
  private static final String WAIVERS_RESOURCE = "/shared-tenant-key-join-waivers.txt";

  private static final String GRANT_READER = "grant-reader";

  /**
   * The classifications a waiver may carry. A bare entry, or one whose reason starts with anything
   * else, is rejected: an entry must say what makes the join safe, not merely that it is.
   */
  private static final List<String> CLASSIFICATIONS =
      List.of(
          "reaches-active-table:", "dead-code", "not-established", "fixed-pending:", GRANT_READER);

  /**
   * A reference to the grants table, not a qualifier: {@code grants gr}, {@code grants AS gr}, or
   * {@code grants} alone, followed by a keyword or punctuation.
   */
  private static final Pattern GRANTS_REFERENCE =
      Pattern.compile(
          "\\bgrants\\b(?!\\s*\\.)(?:\\s+(?:AS\\s+)?([A-Za-z_][A-Za-z_0-9]*))?",
          Pattern.CASE_INSENSITIVE);

  /** A block or line comment, or a string literal: text that names no alias. */
  private static final Pattern SQL_COMMENT_OR_LITERAL =
      Pattern.compile("/\\*.*?\\*/|--[^\\n]*|'(?:[^']|'')*'", Pattern.DOTALL);

  /** {@code alias.column = alias.column}. The only shape this guard judges. */
  private static final Pattern ALIAS_EQUALITY =
      Pattern.compile(
          "\\b([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*([A-Za-z_][A-Za-z_0-9]*)"
              + "\\s*=\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*([A-Za-z_][A-Za-z_0-9]*)\\b");

  /** {@code alias.tenant_id = :param} or {@code = 'literal'}: the alias is pinned to one tenant. */
  private static final Pattern TENANT_PIN =
      Pattern.compile(
          "\\b([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*tenant_id\\s*=\\s*"
              + "([:?][A-Za-z_0-9]*|'[^']*'|CAST\\s*\\([^)]*\\))",
          Pattern.CASE_INSENSITIVE);

  /**
   * {@code alias.key IN (SELECT ...)}, and {@code NOT IN} with it: a correlation, not a join, and
   * the shape a row-value rewrite fixes. The key is matched against a sub-select that need not
   * belong to the same tenant. Inverted, the hazard is inverted too: a foreign row makes the
   * statement EXCLUDE one of this tenant's rows, which is a wrong answer by omission and just as
   * hard to see.
   */
  private static final Pattern SINGLE_COLUMN_IN =
      Pattern.compile(
          "\\b([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*([A-Za-z_][A-Za-z_0-9]*)\\s+(?:NOT\\s+)?IN\\s*\\(\\s*SELECT",
          Pattern.CASE_INSENSITIVE);

  /**
   * {@code (alias.key, alias.tenant_id) IN (SELECT key, tenant_id ...)}: the tenant travels with
   * the key, so the correlation cannot cross a tenant boundary.
   */
  private static final Pattern ROW_VALUE_IN =
      Pattern.compile(
          "\\(\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*,"
              + "\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\.\\s*tenant_id\\s*\\)\\s*IN\\s*\\(",
          Pattern.CASE_INSENSITIVE);

  /** {@code <entry> # <reason>}: the hash that opens the reason is the one with space before it. */
  /** The table an "until-active:<table>" deferral names, token-bounded like the guard baselines. */
  private static final Pattern UNTIL_ACTIVE = Pattern.compile("until-active:([A-Za-z0-9_]+)");

  private static final Pattern WAIVER_LINE = Pattern.compile("(.*?)\\s+#\\s*(.*)");

  /** Spring resolves a SpEL selector into a bind parameter long before the SQL is parsed. */
  private static final Pattern SPEL = Pattern.compile("[:?]#\\{[^}]*}");

  /** {@code FROM table alias} or {@code JOIN table AS alias}: one binding of an alias. */
  private static final Pattern ALIAS_BINDING =
      Pattern.compile(
          "\\b(?:FROM|JOIN)\\s+([A-Za-z_]\\w*)\\s+(?:AS\\s+)?([A-Za-z_]\\w*)",
          Pattern.CASE_INSENSITIVE);

  /** Words that follow a table name without being an alias. */
  private static final Set<String> NOT_AN_ALIAS =
      Set.of(
          "on",
          "where",
          "using",
          "left",
          "right",
          "inner",
          "outer",
          "full",
          "cross",
          "join",
          "lateral",
          "group",
          "order",
          "set",
          "union",
          "select",
          "and",
          "or",
          "limit",
          "having",
          "as",
          "values",
          "returning",
          "for",
          "offset",
          "window",
          "fetch");

  /** Whitespace, {@code +} and plain identifiers or no-argument calls: still one concatenation. */
  private static final Pattern INTERPOLATION =
      Pattern.compile("[\\s+]*(?:[A-Za-z_][\\w.]*(?:\\(\\s*\\))?[\\s+]*)*");

  /** Only a run that looks like a statement or a fragment of one is analysed. */
  private static final Pattern LOOKS_LIKE_SQL =
      Pattern.compile("\\b(FROM|JOIN|WHERE|USING)\\b", Pattern.CASE_INSENSITIVE);

  /** One run of concatenated string literals, with where it starts. */
  record SqlRun(String file, String className, String method, int line, String sql) {
    String origin() {
      return className + "#" + method;
    }
  }

  /** Stands for the anonymous right-hand side of an IN correlation: never in any tenant class. */
  private static final String IN_SUBQUERY = "in-subquery";

  /**
   * One judged predicate: the class it is in, the shared key, and the two aliases it correlates.
   */
  private record Offence(String origin, String column, String aliases, String file, int line) {
    String key() {
      return origin + " " + column + " " + aliases;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The guard
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("every SQL join on a shared key names the tenant, or carries a reasoned waiver")
  void everySharedKeyJoinNamesTheTenantOrIsWaived() throws IOException {
    Map<String, String> sharedKeys = sharedKeys();
    List<SqlRun> corpus = sqlCorpus();
    Map<String, String> waivers = waivers();

    List<Offence> offences = new ArrayList<>();
    for (SqlRun run : corpus) {
      offences.addAll(offences(run, sharedKeys.keySet()));
    }

    Set<String> seen = new TreeSet<>();
    List<String> unwaived = new ArrayList<>();
    for (Offence offence : offences) {
      if (!seen.add(offence.key())) {
        continue;
      }
      if (!waivers.containsKey(offence.key())) {
        unwaived.add(
            offence.key()
                + "  ("
                + offence.file()
                + ":"
                + offence.line()
                + ", owning table "
                + sharedKeys.get(offence.column())
                + ")");
      }
    }

    System.out.println(
        "[shared-key-join] sql-runs="
            + corpus.size()
            + " shared-keys="
            + sharedKeys.size()
            + " predicates-flagged="
            + offences.size()
            + " distinct="
            + seen.size()
            + " waived="
            + waivers.size()
            + " unwaived="
            + unwaived.size()
            + " migration-files-skipped="
            + skippedMigrations);

    assertTrue(
        unwaived.isEmpty(),
        "these SQL predicates join on a key whose value repeats across tenants without constraining"
            + " both sides to the same tenant, so a row owned by another tenant can be matched."
            + " Carry the tenant into the predicate, or add a reasoned waiver to"
            + WAIVERS_RESOURCE
            + ":\n  "
            + String.join("\n  ", unwaived));

    List<String> stale = new ArrayList<>(waivers.keySet());
    stale.removeAll(seen);
    assertTrue(
        stale.isEmpty(),
        "these waivers no longer match any predicate, so the query they excused was fixed, moved or"
            + " renamed. Remove them in the same change that fixed them, so the list cannot become a"
            + " dumping ground:\n  "
            + String.join("\n  ", stale));
  }

  // ---------------------------------------------------------------------------------------------
  // Non-vacuity. A guard that passes because it found nothing is worse than no guard.
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("The guard cannot pass vacuously")
  class NonVacuity {

    @Test
    @DisplayName("given a join on a shared key with no tenant, when analysed, then it is flagged")
    void givenUnscopedSharedKeyJoin_shouldBeFlagged() {
      // ARRANGE
      String sql =
          "SELECT ic.* FROM injectors_contracts ic"
              + " JOIN injectors_contracts_attack_patterns l"
              + " ON l.injector_contract_id = ic.injector_contract_id"
              + " WHERE ic.tenant_id = :tenantId";

      // ACT
      List<Offence> found = offences(run(sql), Set.of("injector_contract_id"));

      // ASSERT: the parent is pinned to one tenant and the link row is not, which is exactly the
      // shape a proximity window calls safe.
      assertEquals(1, found.size(), "expected the unscoped link join to be flagged, got " + found);
      assertEquals("injector_contract_id", found.get(0).column());
    }

    @Test
    @DisplayName(
        "given the same join correlated on tenant_id, when analysed, then it is not flagged")
    void givenTenantCorrelatedSharedKeyJoin_shouldNotBeFlagged() {
      // ARRANGE
      String sql =
          "SELECT ic.* FROM injectors_contracts ic"
              + " JOIN injectors_contracts_attack_patterns l"
              + " ON l.injector_contract_id = ic.injector_contract_id"
              + " AND l.tenant_id = ic.tenant_id"
              + " WHERE ic.tenant_id = :tenantId";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("injector_contract_id")).isEmpty(),
          "a join correlated on tenant_id must not be flagged");
    }

    @Test
    @DisplayName("given two aliases pinned to one parameter, when analysed, then it is not flagged")
    void givenBothSidesPinnedToTheSameParameter_shouldNotBeFlagged() {
      // ARRANGE: the tenant is carried by two independent pins rather than a correlation.
      String sql =
          "SELECT a.tag_name FROM tags a, tags b"
              + " WHERE a.tag_name = b.tag_name AND a.tenant_id = :t AND b.tenant_id = :t";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("tag_name")).isEmpty(),
          "both sides pinned to the same parameter are in one tenant class");
    }

    @Test
    @DisplayName("given a transitive tenant correlation, when analysed, then it is not flagged")
    void givenTransitiveTenantCorrelation_shouldNotBeFlagged() {
      // ARRANGE: a correlates to b, b to c, and the key join is a to c.
      String sql =
          "SELECT 1 FROM injects a JOIN injects b ON b.tenant_id = a.tenant_id"
              + " JOIN injectors_contracts c ON c.tenant_id = b.tenant_id"
              + " AND c.injector_contract_id = a.inject_injector_contract";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("injector_contract_id")).isEmpty(),
          "a tenant class built transitively must be honoured");
    }

    @Test
    @DisplayName("given a shared key correlated through IN (SELECT), then it is flagged")
    void givenSingleColumnInCorrelation_shouldBeFlagged() {
      // ARRANGE: the ids come from a CTE that carries a tenant the correlation ignores.
      String sql =
          "WITH base AS (SELECT i.inject_injector_contract AS contract_id, i.tenant_id FROM injects i)"
              + " SELECT l.attack_pattern_id FROM injectors_contracts_attack_patterns l"
              + " WHERE l.injector_contract_id IN (SELECT contract_id FROM base)";

      // ACT
      List<Offence> found = offences(run(sql), Set.of("injector_contract_id"));

      // ASSERT
      assertEquals(1, found.size(), "expected the IN correlation to be flagged, got " + found);
      assertEquals("injector_contract_id", found.get(0).column());
    }

    @Test
    @DisplayName("given the tenant carried as a row value in the IN, then it is not flagged")
    void givenRowValueInCorrelation_shouldNotBeFlagged() {
      // ARRANGE: the repaired shape, where the tenant travels with the key.
      String sql =
          "WITH base AS (SELECT i.inject_injector_contract AS contract_id, i.tenant_id FROM injects i)"
              + " SELECT l.attack_pattern_id FROM injectors_contracts_attack_patterns l"
              + " WHERE (l.injector_contract_id, l.tenant_id)"
              + " IN (SELECT contract_id, tenant_id FROM base)";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("injector_contract_id")).isEmpty(),
          "a row-value IN that carries the tenant must not be flagged");
    }

    @Test
    @DisplayName(
        "given an IN over bind parameters rather than a sub-select, then it is not flagged")
    void givenInOverParameters_shouldNotBeFlagged() {
      // ARRANGE: a list of ids supplied by the caller is not a cross-tenant correlation.
      String sql =
          "SELECT 1 FROM injectors_contracts ic WHERE ic.injector_contract_id IN (:ids)"
              + " AND ic.tenant_id = :tenantId";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("injector_contract_id")).isEmpty(),
          "an IN over bind parameters is not a correlation");
    }

    @Test
    @DisplayName("given a key that is not shared, when analysed, then it is not flagged")
    void givenGloballyUniqueKey_shouldNotBeFlagged() {
      // ARRANGE
      String sql = "SELECT 1 FROM injects i JOIN injects_tags t ON t.inject_id = i.inject_id";

      // ACT / ASSERT
      assertTrue(
          offences(run(sql), Set.of("injector_contract_id")).isEmpty(),
          "only the keys on the derived list are judged");
    }

    private SqlRun run(String sql) {
      return new SqlRun("fixture", "Fixture", "fixture", 1, sql);
    }
  }

  @Test
  @DisplayName("the source scan sees every native @Query the classpath declares")
  void theScanSeesEveryNativeQueryAnnotation() throws IOException {
    // The scan is the extractor; reflection is the census that refuses a silently empty scan. Both
    // are derived, so neither is a number that rots.
    List<String> declared = reflectiveNativeQueries();
    List<SqlRun> scanned = sqlCorpus();

    assertTrue(
        declared.size() > 100, "the reflective census found almost nothing: " + declared.size());
    assertTrue(
        scanned.size() >= declared.size(),
        "the source scan found "
            + scanned.size()
            + " SQL runs but the classpath declares "
            + declared.size()
            + " native queries, so the scan is missing queries it must judge");

    // What must never be lost is a PREDICATE, not a byte-identical query text. A query assembled
    // from constants (EndpointRepository.ACTIVITY_STATUS_SELECT is itself a SQL fragment spliced
    // into a dozen queries) is reconstructed as two runs rather than one, and both are judged, so
    // demanding the resolved text appear verbatim would fail on a healthy scan. Comparing the
    // alias-to-alias predicates instead asserts exactly the property the guard depends on: every
    // predicate the classpath declares is one the scan also sees.
    // Identity is preserved per declared query: each one must be covered by a SINGLE scanned run,
    // not by the union of all of them. Collapsing every scanned predicate into one global set would
    // let the scan miss a whole query whenever its predicates happen to appear in other queries,
    // and
    // the corpus-size assertion above cannot catch that, because extra fragments inflate the count.
    List<Set<String>> scannedPerRun = new ArrayList<>();
    for (SqlRun run : scanned) {
      scannedPerRun.add(predicates(run.sql()));
    }
    Set<String> unseenAnywhere = new TreeSet<>();
    for (String sql : declared) {
      Set<String> wanted = predicates(sql);
      if (wanted.isEmpty() || scannedPerRun.stream().anyMatch(run -> run.containsAll(wanted))) {
        continue;
      }
      // Not covered by one run. A query spliced from a SQL constant is reconstructed as several
      // runs, which is fine: the guard judges predicates, not whole queries. Only a predicate
      // no run sees at all is a hole.
      for (String predicate : wanted) {
        if (scannedPerRun.stream().noneMatch(run -> run.contains(predicate))) {
          unseenAnywhere.add(predicate);
        }
      }
    }
    assertTrue(
        unseenAnywhere.isEmpty(),
        "the source scan does not see these alias-to-alias predicates that the classpath declares,"
            + " so a query the guard must judge is invisible to it:\n  "
            + String.join("\n  ", unseenAnywhere));
  }

  @Test
  @DisplayName("the scan reaches SQL built in Java, not only annotation values")
  void theScanReachesSqlBuiltInJava() throws IOException {
    // The one site of this family that reflection cannot see builds its SQL in a StringBuilder.
    // If the scan ever stops reconstructing those, the guard silently loses a whole mechanism.
    List<SqlRun> withJoins =
        sqlCorpus().stream()
            .filter(run -> run.className().equals("InjectorContractRepositoryHelper"))
            .filter(run -> run.sql().toUpperCase().contains("JOIN"))
            .toList();
    assertTrue(
        !withJoins.isEmpty(),
        "the scan no longer reconstructs the SQL built in InjectorContractRepositoryHelper");
  }

  @Test
  @DisplayName("a correlation in one sub-select does not excuse its unscoped sibling")
  void aCorrelationInOneSubSelectDoesNotExcuseItsSibling() {
    // ARRANGE: "l" is bound twice, in two sibling sub-selects. The first carries the tenant, the
    // second does not. A statement-wide set of tenant classes would read the first as covering the
    // second, and alias reuse like this is common in the indexing projections.
    String sql =
        "SELECT (SELECT array_agg(l.attack_pattern_id) FROM injectors_contracts_attack_patterns l"
            + " WHERE l.injector_contract_id = ic.injector_contract_id"
            + " AND l.tenant_id = ic.tenant_id) AS patterns,"
            + " (SELECT array_agg(l.domain_id) FROM injectors_contracts_domains l"
            + " WHERE l.injector_contract_id = ic.injector_contract_id) AS domains"
            + " FROM injectors_contracts ic WHERE ic.tenant_id = :tenantId";

    // ACT
    List<Offence> found =
        offences(
            new SqlRun("fixture", "Fixture", "fixture", 1, sql), Set.of("injector_contract_id"));

    // ASSERT: exactly the unscoped sibling, and not the one that carries the tenant.
    assertEquals(1, found.size(), "expected only the unscoped sibling, got " + found);
  }

  @Test
  @DisplayName("an outer correlation still covers a join nested inside it")
  void anOuterCorrelationCoversANestedJoin() {
    // ARRANGE: the tenant pin is at the top level, the key join is inside a sub-select, and an
    // outer
    // alias is visible inside, so this must not be reported.
    String sql =
        "SELECT ic.injector_contract_id, (SELECT count(*) FROM injectors_contracts_domains d"
            + " WHERE d.injector_contract_id = ic.injector_contract_id"
            + " AND d.tenant_id = ic.tenant_id) FROM injectors_contracts ic"
            + " WHERE ic.tenant_id = :tenantId";

    // ACT / ASSERT
    assertTrue(
        offences(
                new SqlRun("fixture", "Fixture", "fixture", 1, sql), Set.of("injector_contract_id"))
            .isEmpty(),
        "a correlation written inside the sub-select that holds the join must cover it");
  }

  @Test
  @DisplayName("every judged predicate resolves to a named owner")
  void everyJudgedPredicateResolvesToANamedOwner() throws IOException {
    // The waiver key is Class#member, so an unresolved owner would collapse every unresolved
    // statement of a class onto one waiver, which is the ambiguity the member was added to remove.
    // Only a run that actually produces a predicate has to resolve: the extraction is deliberately
    // permissive about what looks like SQL, so a @Schema description carrying the word "from" is in
    // the corpus and costs nothing, because prose holds no alias-to-alias predicate.
    Map<String, String> sharedKeys = sharedKeys();
    List<String> unresolved = new ArrayList<>();
    for (SqlRun run : sqlCorpus()) {
      if (!offences(run, sharedKeys.keySet()).isEmpty() && run.method().equals("unknown")) {
        unresolved.add(run.file() + ":" + run.line());
      }
    }
    assertTrue(
        unresolved.isEmpty(),
        "the owning member could not be resolved for these judged statements, so a waiver on them"
            + " would not name one statement:\n  "
            + String.join("\n  ", unresolved));
  }

  @Test
  @DisplayName("the shared-key list is non-empty, well formed and carries the known keys")
  void theSharedKeyListIsWellFormed() throws IOException {
    Map<String, String> keys = sharedKeys();
    assertTrue(keys.size() > 15, "the shared-key list looks truncated: " + keys);
    // Named rather than counted: a schema change may legitimately move the count, but losing the
    // per-tenant uniqueness of a contract id would be a schema decision, not a silent drift.
    assertEquals("injectors_contracts", keys.get("injector_contract_id"));
    assertEquals("attack_patterns", keys.get("attack_pattern_external_id"));
  }

  /**
   * The rules a single waiver must satisfy, as a pure function so each one can be shown to fire.
   * {@code activeTables} is the production activation list: the two reasons that depend on it
   * expire mechanically rather than when someone remembers them.
   */
  static List<String> waiverViolations(String key, String reason, Set<String> activeTables) {
    return waiverViolations(key, reason, activeTables, Map.of());
  }

  static List<String> waiverViolations(
      String key,
      String reason,
      Set<String> activeTables,
      Map<String, Set<String>> groupTenantGrantAliases) {
    List<String> malformed = new ArrayList<>();
    if (reason.isBlank()) {
      malformed.add(key + " :: no reason at all");
      return malformed;
    }
    if (CLASSIFICATIONS.stream().noneMatch(reason::startsWith)) {
      malformed.add(key + " :: reason does not begin with a classification: " + reason);
      return malformed;
    }
    // A classification is a label, not an argument: each one must be followed by prose that says
    // what makes the join safe, so a reason cannot be a bare tag.
    String rest = reason;
    for (String classification : CLASSIFICATIONS) {
      if (reason.startsWith(classification)) {
        rest = reason.substring(classification.length());
      }
    }
    if (rest.replaceAll("[^A-Za-z]", "").length() < 25) {
      malformed.add(key + " :: classification with no reason behind it: " + reason);
    }
    if (reason.startsWith("not-established") && !reason.contains("until-active:")) {
      malformed.add(
          key
              + " :: an unestablished waiver must name the table whose activation retires it,"
              + " as until-active:<table>");
    }
    if (reason.startsWith("reaches-active-table:")
        && reason.substring("reaches-active-table:".length()).split("[ ,:]")[0].isBlank()) {
      malformed.add(key + " :: reaches-active-table must name the table");
    }
    if (reason.startsWith("fixed-pending:")
        && !reason.substring("fixed-pending:".length()).matches("#\\d+\\b.*")) {
      malformed.add(
          key
              + " :: fixed-pending must name the pull request that repairs it, as"
              + " fixed-pending:#<number>");
    }
    // The two reasons that depend on the activation list expire with it, so the list decides,
    // not whoever does the activation. Without this the format check passes forever: a waiver
    // could claim attenuation by a table nobody activated, and a deferral could outlive the
    // activation that was supposed to retire it, both on a green build.
    if (reason.startsWith("reaches-active-table:")) {
      String named = reason.substring("reaches-active-table:".length()).split("[ ,:]")[0].trim();
      if (!named.isBlank() && !activeTables.contains(named.toLowerCase(Locale.ROOT))) {
        malformed.add(
            key
                + " :: reaches-active-table names "
                + named
                + ", which is not in openaev.tenant.active-tables. The attenuation this waiver"
                + " claims does not hold, so the join is unprotected");
      }
    }
    // grants has no tenant column, so this analyser can never place a grant in a tenant class. The
    // grant reader guard checks the one rule that makes such a join correct, the grant's group
    // restricted to the row's tenant or to the platform, so this waiver is only as good as that
    // listing, and only for the join that reaches grants.
    if (reason.startsWith(GRANT_READER)) {
      String origin = key.split(" ")[0];
      Set<String> grantAliases = groupTenantGrantAliases.get(origin);
      if (grantAliases == null) {
        malformed.add(
            key
                + " :: grant-reader, but "
                + origin
                + " is not listed as group-tenant in grant-readers.txt, so nothing checks that the"
                + " grant's group is restricted to the tenant");
      } else {
        String[] aliases =
            key.substring(key.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT).split("=");
        if (Arrays.stream(aliases).noneMatch(grantAliases::contains)) {
          malformed.add(
              key
                  + " :: grant-reader, but the waived join does not join the grants alias "
                  + grantAliases
                  + " of that statement");
        }
      }
    }
    Matcher untilActive = UNTIL_ACTIVE.matcher(reason);
    if (untilActive.find()) {
      String named = untilActive.group(1).toLowerCase(Locale.ROOT);
      if (activeTables.contains(named)) {
        malformed.add(
            key
                + " :: until-active names "
                + named
                + ", which is now active. The deferral has expired: settle this join or"
                + " reclassify it");
      }
    }
    return malformed;
  }

  @Test
  @DisplayName("every waiver carries a classification and a reason")
  void everyWaiverCarriesAClassificationAndAReason() throws IOException {
    List<String> malformed = new ArrayList<>();
    Set<String> activeTables = ProductionActiveTables.names();
    Map<String, String> waivers = waivers();
    Map<String, Set<String>> groupTenantGrantAliases = groupTenantGrantAliases(waivers);
    malformed.addAll(grantReaderWaiversBeyondOnePerStatement(waivers));
    for (Map.Entry<String, String> waiver : waivers.entrySet()) {
      malformed.addAll(
          waiverViolations(
              waiver.getKey(), waiver.getValue(), activeTables, groupTenantGrantAliases));
    }
    assertTrue(
        malformed.isEmpty(),
        "these waivers do not carry a usable reason:\n  " + String.join("\n  ", malformed));
  }

  // ---------------------------------------------------------------------------------------------
  // The analyser
  // ---------------------------------------------------------------------------------------------

  /**
   * Flags every {@code alias.key = alias.column} predicate on a shared key whose two aliases are
   * not in the same tenant-equality class of the statement.
   */
  private static List<Offence> offences(SqlRun run, Set<String> sharedKeys) {
    String sql = normalise(run.sql());
    int[] scopeAt = scopeIds(sql);
    Map<Integer, Integer> scopeParent = scopeParents(sql, scopeAt);

    // Every tenant correlation and every tenant pin, each remembered with the scope it was written
    // in. A correlation inside one sub-select says nothing about a sibling sub-select, so it is not
    // allowed to excuse one: that is the whole reason the scope is carried rather than a single
    // statement-wide set of classes. Alias reuse between siblings is common here, and a
    // statement-wide union would let the scoped sibling cover the unscoped one.
    List<int[]> correlationScopes = new ArrayList<>();
    List<String[]> correlations = new ArrayList<>();
    List<String[]> keyJoins = new ArrayList<>();
    List<Integer> keyJoinScopes = new ArrayList<>();

    Matcher equality = ALIAS_EQUALITY.matcher(sql);
    while (equality.find()) {
      String leftAlias = equality.group(1);
      String leftColumn = equality.group(2);
      String rightAlias = equality.group(3);
      String rightColumn = equality.group(4);
      if (leftColumn.equalsIgnoreCase("tenant_id") && rightColumn.equalsIgnoreCase("tenant_id")) {
        correlations.add(new String[] {leftAlias, rightAlias});
        correlationScopes.add(new int[] {scopeAt[equality.start()]});
      } else if (sharedKeys.contains(leftColumn) || sharedKeys.contains(rightColumn)) {
        String column = sharedKeys.contains(leftColumn) ? leftColumn : rightColumn;
        keyJoins.add(new String[] {column, leftAlias, rightAlias});
        keyJoinScopes.add(scopeAt[equality.start()]);
      }
    }

    Matcher pin = TENANT_PIN.matcher(sql);
    while (pin.find()) {
      correlations.add(new String[] {pin.group(1), "value:" + pin.group(2).toLowerCase()});
      correlationScopes.add(new int[] {scopeAt[pin.start()]});
    }

    // A correlation through IN (SELECT ...) is the same hazard with a different syntax: the key is
    // matched against ids the sub-select produces, which need not be this tenant's. It is safe only
    // when the tenant travels with the key as a row value, which is also how it is repaired.
    Set<String> tenantPaired = new HashSet<>();
    Matcher rowValue = ROW_VALUE_IN.matcher(sql);
    while (rowValue.find()) {
      if (rowValue.group(1).equals(rowValue.group(3))) {
        tenantPaired.add(rowValue.group(1) + "." + rowValue.group(2));
      }
    }
    Matcher singleIn = SINGLE_COLUMN_IN.matcher(sql);
    while (singleIn.find()) {
      String alias = singleIn.group(1);
      String column = singleIn.group(2);
      if (sharedKeys.contains(column) && !tenantPaired.contains(alias + "." + column)) {
        keyJoins.add(new String[] {column, alias, IN_SUBQUERY});
        keyJoinScopes.add(scopeAt[singleIn.start()]);
      }
    }

    List<Offence> found = new ArrayList<>();
    for (int index = 0; index < keyJoins.size(); index++) {
      String[] join = keyJoins.get(index);
      if (join[1].equals(join[2])) {
        // A self-correlation inside one alias cannot cross a tenant boundary.
        continue;
      }
      Set<Integer> visible = ancestors(keyJoinScopes.get(index), scopeParent);
      Map<String, String> parent = new HashMap<>();
      for (int c = 0; c < correlations.size(); c++) {
        if (visible.contains(correlationScopes.get(c)[0])) {
          union(parent, correlations.get(c)[0], correlations.get(c)[1]);
        }
      }
      if (!find(parent, join[1]).equals(find(parent, join[2]))) {
        String aliases =
            join[1].compareTo(join[2]) <= 0 ? join[1] + "=" + join[2] : join[2] + "=" + join[1];
        found.add(new Offence(run.origin(), join[0], aliases, run.file(), run.line()));
      }
    }
    return found;
  }

  /**
   * The scope id of every character, where a scope is a parenthesised group. An alias bound inside
   * a group is invisible outside it, so a predicate's scope and its ancestors are exactly the
   * places a correlation may come from.
   */
  private static int[] scopeIds(String sql) {
    int[] scopeAt = new int[sql.length() + 1];
    Deque<Integer> open = new ArrayDeque<>();
    int next = 1;
    int current = 0;
    for (int index = 0; index < sql.length(); index++) {
      char c = sql.charAt(index);
      if (c == '(') {
        open.push(current);
        current = next++;
      }
      scopeAt[index] = current;
      if (c == ')' && !open.isEmpty()) {
        current = open.pop();
      }
    }
    scopeAt[sql.length()] = current;
    return scopeAt;
  }

  /** Each scope's enclosing scope, read back off the same walk. */
  private static Map<Integer, Integer> scopeParents(String sql, int[] scopeAt) {
    Map<Integer, Integer> parents = new HashMap<>();
    Deque<Integer> open = new ArrayDeque<>();
    int current = 0;
    for (int index = 0; index < sql.length(); index++) {
      char c = sql.charAt(index);
      if (c == '(') {
        parents.put(scopeAt[index], current);
        open.push(current);
        current = scopeAt[index];
      } else if (c == ')' && !open.isEmpty()) {
        current = open.pop();
      }
    }
    return parents;
  }

  private static Set<Integer> ancestors(int scope, Map<Integer, Integer> parents) {
    Set<Integer> visible = new LinkedHashSet<>();
    Integer current = scope;
    while (current != null && visible.add(current)) {
      current = parents.get(current);
    }
    return visible;
  }

  /** Every {@code alias.column = alias.column} predicate of a statement, normalised. */
  private static Set<String> predicates(String sql) {
    Set<String> found = new TreeSet<>();
    Matcher equality = ALIAS_EQUALITY.matcher(normalise(sql));
    while (equality.find()) {
      found.add(
          equality.group(1)
              + "."
              + equality.group(2)
              + "="
              + equality.group(3)
              + "."
              + equality.group(4));
    }
    return found;
  }

  /**
   * JSqlParser round trip, which drops comments and normalises whitespace so neither can hide a
   * predicate from the matcher. A fragment that does not parse on its own is analysed as written: a
   * StringBuilder piece is legitimately not a statement, and refusing to judge it would be a hole.
   */
  private static String normalise(String sql) {
    String withoutSpel = SPEL.matcher(sql).replaceAll("?");
    try {
      return CCJSqlParserUtil.parse(withoutSpel).toString();
    } catch (Exception notAStatement) {
      return withoutSpel;
    }
  }

  private static String find(Map<String, String> parent, String node) {
    String current = node;
    while (!current.equals(parent.getOrDefault(current, current))) {
      current = parent.get(current);
    }
    return current;
  }

  private static void union(Map<String, String> parent, String left, String right) {
    String leftRoot = find(parent, left);
    String rightRoot = find(parent, right);
    if (!leftRoot.equals(rightRoot)) {
      parent.put(leftRoot, rightRoot);
    }
  }

  private static String squash(String sql) {
    return SPEL.matcher(sql).replaceAll("?").replaceAll("\\s+", " ").trim();
  }

  // ---------------------------------------------------------------------------------------------
  // Extraction
  // ---------------------------------------------------------------------------------------------

  /** Every production module's sources, derived so a new module cannot silently escape the scan. */
  static List<Path> productionRoots() throws IOException {
    try (Stream<Path> modules = Files.list(Path.of("..").toAbsolutePath().normalize())) {
      return modules
          .filter(module -> module.getFileName().toString().startsWith("openaev-"))
          .map(module -> module.resolve("src/main/java"))
          .filter(Files::isDirectory)
          .toList();
    }
  }

  /**
   * Every run of adjacent string literals in the production sources that looks like SQL. Adjacent
   * means separated by nothing but whitespace and {@code +}, which is how both an annotation value
   * and a StringBuilder argument are written, so one mechanism covers SQL whatever carries it.
   */
  private static int skippedMigrations;

  static List<SqlRun> sqlCorpus() throws IOException {
    List<SqlRun> runs = new ArrayList<>();
    skippedMigrations = 0;
    for (Path root : productionRoots()) {
      assertTrue(Files.isDirectory(root), "production root vanished: " + root);
      try (Stream<Path> sources = Files.walk(root)) {
        for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
          if (source.toString().contains("/migration/")) {
            // A Flyway migration runs before any tenant scope exists and rewrites rows across every
            // tenant on purpose, so judging it by this rule would be wrong rather than strict. Its
            // joins are reviewed as their own class, by the migration review, and the count is
            // printed below so the exclusion is visible rather than silent.
            skippedMigrations++;
            continue;
          }
          runs.addAll(sqlRuns(source));
        }
      }
    }
    // Both production modules must be in the scan; a renamed or dropped module cannot silently
    // shrink the corpus to nothing.
    assertTrue(
        runs.stream().anyMatch(run -> run.file().contains("openaev-model")),
        "no SQL found in openaev-model, which holds the repository layer");
    return runs;
  }

  private static List<SqlRun> sqlRuns(Path source) throws IOException {
    String text = Files.readString(source);
    String className = source.getFileName().toString().replace(".java", "");
    String file = relative(source);
    List<SqlRun> runs = new ArrayList<>();

    StringBuilder current = new StringBuilder();
    int runStart = -1;
    int runEnd = -1;
    int previousEnd = -1;
    for (Literal literal : literals(text)) {
      boolean adjacent = previousEnd >= 0 && isConcatenation(text, previousEnd, literal.start());
      if (!adjacent) {
        flush(runs, file, className, text, runStart, runEnd, current);
        current.setLength(0);
        runStart = literal.start();
      }
      if (adjacent) {
        // A space stands in for the interpolated expression, so its absence cannot weld the token
        // before it to the token after it.
        current.append(' ');
      }
      current.append(literal.text());
      previousEnd = literal.end();
      runEnd = literal.end();
    }
    flush(runs, file, className, text, runStart, runEnd, current);
    return runs;
  }

  /**
   * Two literals belong to one run when a {@code +} joins them and nothing but whitespace, an
   * identifier or a no-argument call sits in between. A constant spliced into a query ({@code "...
   * payload_type = '" + DNS_RESOLUTION_TYPE + "' ..."}) must not split the run: half a statement
   * analysed on its own loses the tenant pins of the other half, which is a false positive, and the
   * two destructive pruning statements of this family are written exactly that way. A comma or a
   * semicolon ends the run, because that is a new argument or a new statement.
   */
  private static boolean isConcatenation(String text, int from, int to) {
    String between = text.substring(from, to);
    return between.indexOf('+') >= 0 && INTERPOLATION.matcher(between).matches();
  }

  private static void flush(
      List<SqlRun> runs,
      String file,
      String className,
      String text,
      int start,
      int end,
      StringBuilder sql) {
    if (start < 0 || sql.length() < 20) {
      return;
    }
    String joined = sql.toString();
    if (!LOOKS_LIKE_SQL.matcher(joined).find()) {
      return;
    }
    int line = (int) text.substring(0, start).chars().filter(c -> c == '\n').count() + 1;
    runs.add(new SqlRun(file, className, owningMethod(text, start, end), line, joined));
  }

  /** One string literal or text block: its source span and its decoded content. */
  private record Literal(int start, int end, String text) {}

  /**
   * Java string literals and text blocks, scanned by hand rather than matched. A regex over escaped
   * content recurses once per character and overflows the stack on the long SQL text blocks this
   * repository uses, and scanning also lets comments and char literals be skipped properly, so a
   * commented-out join cannot be read as live SQL.
   */
  private static List<Literal> literals(String text) {
    List<Literal> found = new ArrayList<>();
    int index = 0;
    int length = text.length();
    while (index < length) {
      char c = text.charAt(index);
      if (c == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
        int end = text.indexOf('\n', index);
        index = end < 0 ? length : end + 1;
      } else if (c == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
        int end = text.indexOf("*/", index + 2);
        index = end < 0 ? length : end + 2;
      } else if (c == '\'') {
        index = skipCharLiteral(text, index);
      } else if (c == '"' && text.startsWith("\"\"\"", index)) {
        int bodyStart = text.indexOf('\n', index + 3);
        int end = bodyStart < 0 ? -1 : text.indexOf("\"\"\"", bodyStart);
        if (end < 0) {
          index = length;
        } else {
          found.add(new Literal(index, end + 3, stripIndent(text.substring(bodyStart + 1, end))));
          index = end + 3;
        }
      } else if (c == '"') {
        int cursor = index + 1;
        StringBuilder body = new StringBuilder();
        while (cursor < length && text.charAt(cursor) != '"') {
          if (text.charAt(cursor) == '\\' && cursor + 1 < length) {
            body.append(unescape(text.charAt(cursor + 1)));
            cursor += 2;
          } else {
            body.append(text.charAt(cursor));
            cursor++;
          }
        }
        found.add(new Literal(index, Math.min(cursor + 1, length), body.toString()));
        index = Math.min(cursor + 1, length);
      } else {
        index++;
      }
    }
    return found;
  }

  private static int skipCharLiteral(String text, int index) {
    int cursor = index + 1;
    while (cursor < text.length() && text.charAt(cursor) != '\'') {
      cursor += text.charAt(cursor) == '\\' ? 2 : 1;
    }
    return Math.min(cursor + 1, text.length());
  }

  /**
   * A field or constant declaration at member indentation: {@code String NAME = } or {@code T n;}.
   */
  private static final Pattern FIELD_DECLARATION =
      Pattern.compile(
          "(?m)^ {2,}(?:@\\w+\\s+)*[A-Za-z_][\\w<>\\[\\],.?\\s]*?\\s([A-Za-z_]\\w*)\\s*[=;]");

  /** A method declaration: two or more tokens before the parenthesis, at member indentation. */
  private static final Pattern METHOD_DECLARATION =
      Pattern.compile(
          "(?m)^ {2,}(?:(?:public|protected|private|static|final|default|abstract|synchronized)\\s+)*"
              + "([A-Za-z_][\\w<>\\[\\],.?\\s]*?)\\s+([a-z]\\w*)\\s*\\(");

  /** {@code @Query(} or {@code @Query(value =} immediately before the run. */
  private static final Pattern ANNOTATION_VALUE =
      Pattern.compile("@Query\\s*\\(\\s*(?:value\\s*=\\s*)?$");

  private static final Set<String> NOT_A_RETURN_TYPE =
      Set.of("return", "if", "while", "for", "throw", "new", "else", "switch", "catch", "assert");

  /**
   * The method a SQL run belongs to, so a waiver names one statement and not every statement of the
   * class that happens to use the same aliases. {@code InjectRepository} correlates {@code ic} to
   * {@code icap} on the contract id in two unrelated statements, an indexing projection and a
   * destructive prune; without the method they would share a single waiver and fixing one would not
   * retire it.
   *
   * <p>An annotation value is followed by its method, and SQL built in a body is preceded by the
   * method that builds it, so the direction is chosen by looking at what sits before the run.
   */
  static String owningMethod(String text, int start, int end) {
    String before = text.substring(Math.max(0, start - 200), start);
    boolean annotationValue = ANNOTATION_VALUE.matcher(before.stripTrailing()).find();
    String method = nearestMember(METHOD_DECLARATION, text, start, end, annotationValue, true);
    if (!method.equals("unknown")) {
      return method;
    }
    // SQL also lives on fields: a @Formula on an entity attribute, and a constant fragment spliced
    // into several queries. Either is a precise enough owner for a waiver to name one statement.
    return nearestMember(FIELD_DECLARATION, text, start, end, annotationValue, false);
  }

  private static String nearestMember(
      Pattern declaration,
      String text,
      int start,
      int end,
      boolean annotationValue,
      boolean isMethod) {
    Matcher member = declaration.matcher(text);
    String nearestBefore = "unknown";
    while (member.find()) {
      int nameGroup = isMethod ? 2 : 1;
      if (isMethod
          && (NOT_A_RETURN_TYPE.contains(member.group(1).trim())
              || text.lastIndexOf('=', member.start(2)) > member.start())) {
        continue;
      }
      if (annotationValue && member.start() >= end) {
        return member.group(nameGroup);
      }
      if (member.end() <= start) {
        nearestBefore = member.group(nameGroup);
      }
    }
    return nearestBefore;
  }

  /** Text-block incidental indentation, the way javac strips it: the common leading run. */
  private static String stripIndent(String body) {
    return body.stripIndent();
  }

  private static String unescape(char escaped) {
    return switch (escaped) {
      case 'n' -> "\n";
      case 'r' -> "\r";
      case 't' -> "\t";
      default -> String.valueOf(escaped);
    };
  }

  static String relative(Path source) {
    String full = source.toAbsolutePath().normalize().toString();
    int module = full.indexOf("openaev-");
    return module < 0 ? full : full.substring(module);
  }

  /** The census: every {@code @Query(nativeQuery = true)} the classpath declares. */
  private static List<String> reflectiveNativeQueries() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.openaev");
    List<String> found = new ArrayList<>();
    for (JavaClass javaClass : classes) {
      if (!javaClass.isInterface()) {
        continue;
      }
      Class<?> repository = javaClass.reflect();
      if (!Repository.class.isAssignableFrom(repository)) {
        continue;
      }
      for (Method method : repository.getDeclaredMethods()) {
        Query query = method.getAnnotation(Query.class);
        if (query != null && query.nativeQuery() && !query.value().isBlank()) {
          found.add(query.value());
        }
      }
    }
    return found;
  }

  // ---------------------------------------------------------------------------------------------
  // Resources
  // ---------------------------------------------------------------------------------------------

  /** column to owning table. */
  private static Map<String, String> sharedKeys() throws IOException {
    Map<String, String> keys = new LinkedHashMap<>();
    for (String line : lines(KEYS_RESOURCE)) {
      String[] parts = line.split("\\s+");
      assertEquals(2, parts.length, "a shared-key line must be '<table> <column>', got: " + line);
      keys.put(parts[1], parts[0]);
    }
    return keys;
  }

  /**
   * The aliases of grants in each statement a grant-reader waiver names and the grant reader
   * allowlist lists as group-tenant, keyed by the statement's owner. Only those statements are
   * read, so a group-tenant reader no waiver names is never judged here. A waiver names its owner
   * and its join, not the scanned run the join came from, so an owner scanned as more than one run
   * is refused: an alias of one run could otherwise vouch for a join of another.
   */
  private static Map<String, Set<String>> groupTenantGrantAliases(Map<String, String> waivers)
      throws IOException {
    Set<String> named = new HashSet<>();
    waivers.forEach(
        (key, reason) -> {
          if (reason.startsWith(GRANT_READER)) {
            named.add(key.split(" ")[0]);
          }
        });
    named.retainAll(GrantReaderAllowlistArchTest.groupTenantReaders());
    Map<String, Set<String>> aliases = new HashMap<>();
    for (SqlRun run : sqlCorpus()) {
      if (named.contains(run.origin())) {
        if (aliases.containsKey(run.origin())) {
          throw new IllegalStateException(
              run.origin()
                  + " is scanned as more than one run, so its grants alias cannot be tied to the"
                  + " waived join");
        }
        aliases.put(run.origin(), grantsAliasesOf(run.origin(), run.sql()));
      }
    }
    return aliases;
  }

  /**
   * The alias {@code sql} gives the grants table. A waiver names a join by its two aliases only,
   * not by the sub-select it sits in, and the grant reader guard finds the group restriction
   * anywhere in the statement, not on one alias. So the statement must give grants one alias, and
   * that alias must appear once in the statement other than as a qualifier ({@code gr.column}): a
   * second grants alias could carry no restriction, and the alias given again to another table, in
   * any FROM or JOIN form, could pass a join of that table for the grants join. A grants table
   * given no alias is named by itself. Comments and string literals are removed first; any other
   * unqualified use of the name is refused too.
   */
  static Set<String> grantsAliasesOf(String origin, String sql) {
    String statement = SQL_COMMENT_OR_LITERAL.matcher(normalise(sql)).replaceAll(" ");
    Set<String> grantsAliases = new HashSet<>();
    Matcher grants = GRANTS_REFERENCE.matcher(statement);
    int references = 0;
    while (grants.find()) {
      references++;
      // With no alias, the table is named by itself.
      String alias = grants.group(1) == null ? "grants" : grants.group(1).toLowerCase(Locale.ROOT);
      grantsAliases.add(NOT_AN_ALIAS.contains(alias) ? "grants" : alias);
    }
    if (references > 1) {
      throw new IllegalStateException(
          origin
              + " references grants more than once "
              + grantsAliases
              + ", so the group restriction cannot be tied to the waived join");
    }
    for (String alias : grantsAliases) {
      Matcher unqualified =
          Pattern.compile("\\b" + Pattern.quote(alias) + "\\b(?!\\s*\\.)", Pattern.CASE_INSENSITIVE)
              .matcher(statement);
      int declarations = 0;
      while (unqualified.find()) {
        declarations++;
      }
      if (declarations != 1) {
        throw new IllegalStateException(
            origin
                + " uses the grants alias "
                + alias
                + " other than as a qualifier more than once, so it cannot be tied to the waived"
                + " join");
      }
    }
    return grantsAliases;
  }

  /**
   * The statements carrying more than one grant-reader waiver. The grant reader guard finds the
   * group restriction somewhere in the statement, not on a given correlation of the grants alias,
   * so it vouches for one grants join per statement: a second correlation of the same alias, waived
   * in turn, could carry no restriction.
   */
  static List<String> grantReaderWaiversBeyondOnePerStatement(Map<String, String> waivers) {
    Map<String, List<String>> byStatement = new LinkedHashMap<>();
    waivers.forEach(
        (key, reason) -> {
          if (reason.startsWith(GRANT_READER)) {
            byStatement.computeIfAbsent(key.split(" ")[0], origin -> new ArrayList<>()).add(key);
          }
        });
    List<String> found = new ArrayList<>();
    byStatement.forEach(
        (origin, keys) -> {
          if (keys.size() > 1) {
            found.add(
                origin
                    + " :: "
                    + keys.size()
                    + " grant-reader waivers, but the grant reader guard vouches for one grants"
                    + " join per statement: "
                    + keys);
          }
        });
    return found;
  }

  /** waiver key to reason. */
  private static Map<String, String> waivers() throws IOException {
    Map<String, String> waivers = new LinkedHashMap<>();
    Set<String> duplicates = new LinkedHashSet<>();
    for (String line : lines(WAIVERS_RESOURCE)) {
      // The reason is introduced by a hash PRECEDED by whitespace: the entry itself carries one, in
      // Class#method, so splitting on the first hash would cut the entry in half and collapse every
      // statement of a class onto one key.
      Matcher split = WAIVER_LINE.matcher(line);
      String entry = split.matches() ? split.group(1).trim() : line.trim();
      String reason = split.matches() ? split.group(2).trim() : "";
      assertTrue(!entry.isEmpty(), "a waiver line must carry an entry before its reason: " + line);
      if (waivers.put(entry, reason) != null) {
        duplicates.add(entry);
      }
    }
    assertTrue(duplicates.isEmpty(), "duplicate waiver entries: " + duplicates);
    return waivers;
  }

  private static List<String> lines(String resource) throws IOException {
    try (InputStream stream = SharedTenantKeyJoinArchTest.class.getResourceAsStream(resource)) {
      assertTrue(stream != null, "missing test resource " + resource);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    }
  }

  @Nested
  @DisplayName("A waiver that depends on the activation list expires with it")
  class WaiverExpiry {

    private static final Set<String> ACTIVE = Set.of("attack_patterns", "documents");

    @Test
    @DisplayName("given reaches-active-table on a table that is not active, then it is flagged")
    void givenAttenuationByAnInactiveTable_shouldBeFlagged() {
      List<String> found =
          waiverViolations(
              "SomeRepository#someQuery shared_key a=b",
              "reaches-active-table:injectors_contracts : the chain goes on to that table so a"
                  + " foreign row is removed there before it can matter here",
              ACTIVE);
      assertTrue(
          found.stream().anyMatch(v -> v.contains("not in openaev.tenant.active-tables")),
          "a waiver claiming attenuation by a table nobody activated must fail. Got: " + found);
    }

    @Test
    @DisplayName("given reaches-active-table on a table that is active, then it is not flagged")
    void givenAttenuationByAnActiveTable_shouldNotBeFlagged() {
      List<String> found =
          waiverViolations(
              "SomeRepository#someQuery shared_key a=b",
              "reaches-active-table:attack_patterns : the chain goes on to that table so a foreign"
                  + " row is removed there before it can matter here",
              ACTIVE);
      assertTrue(found.isEmpty(), "an attenuation by an active table is legitimate. Got: " + found);
    }

    @Test
    @DisplayName("given until-active on a table that is now active, then it is flagged")
    void givenDeferralOnAnActivatedTable_shouldBeFlagged() {
      List<String> found =
          waiverViolations(
              "SomeRepository#someQuery shared_key a=b",
              "not-established until-active:documents : nobody has measured whether this one can"
                  + " actually see another tenant row, so it waits for that activation",
              ACTIVE);
      assertTrue(
          found.stream().anyMatch(v -> v.contains("is now active")),
          "a deferral whose table was activated must fail. Got: " + found);
    }

    @Test
    @DisplayName("given until-active on a table that is still inactive, then it is not flagged")
    void givenDeferralOnAnInactiveTable_shouldNotBeFlagged() {
      List<String> found =
          waiverViolations(
              "SomeRepository#someQuery shared_key a=b",
              "not-established until-active:injectors_contracts : nobody has measured whether this"
                  + " one can actually see another tenant row, so it waits for that activation",
              ACTIVE);
      assertTrue(
          found.isEmpty(), "a deferral on a table still inactive is legitimate. Got: " + found);
    }
  }

  @Nested
  @DisplayName("A waiver on a grants join defers to the grant reader allowlist")
  class GrantReaderWaiver {

    private static final String KEY =
        "SomeRepository#someGrantedQuery injector_contract_id gr=injcon";
    private static final String REASON =
        "grant-reader : grants has no tenant column; the statement restricts the grant's group to"
            + " the contract's tenant or to a platform group";

    @Test
    @DisplayName("given a statement listed group-tenant and its grants alias, then it is accepted")
    void given_groupTenantReaderOnItsGrantsAlias_should_beAccepted() {
      // Arrange
      Map<String, Set<String>> listed = Map.of("SomeRepository#someGrantedQuery", Set.of("gr"));

      // Act
      List<String> found = waiverViolations(KEY, REASON, Set.of(), listed);

      // Assert
      assertTrue(found.isEmpty(), "a listed group-tenant reader is excused. Got: " + found);
    }

    @Test
    @DisplayName("given a statement not listed group-tenant, then it is flagged")
    void given_statementNotListedGroupTenant_should_beFlagged() {
      // Arrange
      Map<String, Set<String>> listed = Map.of("OtherRepository#otherQuery", Set.of("gr"));

      // Act
      List<String> found = waiverViolations(KEY, REASON, Set.of(), listed);

      // Assert
      assertTrue(
          found.stream().anyMatch(v -> v.contains("not listed as group-tenant")),
          "a grant-reader waiver needs the grant guard to vouch for it. Got: " + found);
    }

    @Test
    @DisplayName("given a join that does not reach the grants alias, then it is flagged")
    void given_joinNotOnTheGrantsAlias_should_beFlagged() {
      // Arrange
      Map<String, Set<String>> listed = Map.of("SomeRepository#someGrantedQuery", Set.of("g2"));

      // Act
      List<String> found = waiverViolations(KEY, REASON, Set.of(), listed);

      // Assert
      assertTrue(
          found.stream().anyMatch(v -> v.contains("does not join the grants alias")),
          "grant-reader excuses the grants join only, never another join of the same statement."
              + " Got: "
              + found);
    }

    @Test
    @DisplayName("given a statement declaring its grants alias once, then the alias is returned")
    void given_grantsAliasDeclaredOnce_should_beReturned() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id)";

      // Act
      Set<String> aliases = grantsAliasesOf("SomeRepository#someGrantedQuery", sql);

      // Assert
      assertEquals(Set.of("gr"), aliases);
    }

    @Test
    @DisplayName("given a statement giving grants two aliases, then it is refused")
    void given_twoGrantsAliases_should_beRefused() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id) OR EXISTS (SELECT 1 FROM"
              + " grants gr2 WHERE gr2.grant_resource = injcon.injector_contract_id)";

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> grantsAliasesOf("SomeRepository#someGrantedQuery", sql));
    }

    @Test
    @DisplayName(
        "given a sibling sub-select reusing the grants alias for another table, then it is refused")
    void given_grantsAliasReusedForAnotherTable_should_beRefused() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id) AND EXISTS (SELECT 1 FROM"
              + " injectors_contracts_attack_patterns gr"
              + " WHERE gr.injector_contract_id = injcon.injector_contract_id)";

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> grantsAliasesOf("SomeRepository#someGrantedQuery", sql));
    }

    @Test
    @DisplayName(
        "given a sibling comma join reusing the grants alias for another table, then it is refused")
    void given_grantsAliasReusedInACommaJoin_should_beRefused() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id) AND EXISTS (SELECT 1 FROM"
              + " users u2, injectors_contracts_attack_patterns gr"
              + " WHERE gr.injector_contract_id = injcon.injector_contract_id)";

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> grantsAliasesOf("SomeRepository#someGrantedQuery", sql));
    }

    @Test
    @DisplayName(
        "given a comment between another table and the reused grants alias, then it is refused")
    void given_grantsAliasReusedAfterAComment_should_beRefused() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id) AND EXISTS (SELECT 1 FROM"
              + " injectors_contracts_attack_patterns /* mapping */ gr"
              + " WHERE gr.injector_contract_id = injcon.injector_contract_id)";

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> grantsAliasesOf("SomeRepository#someGrantedQuery", sql));
    }

    @Test
    @DisplayName("given a grants table with no alias, then it is named by itself")
    void given_grantsWithoutAlias_should_beNamedByItself() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants"
              + " WHERE grants.grant_resource = injcon.injector_contract_id)";

      // Act
      Set<String> aliases = grantsAliasesOf("SomeRepository#someGrantedQuery", sql);

      // Assert
      assertEquals(Set.of("grants"), aliases);
    }

    @Test
    @DisplayName("given an aliased and an unaliased grants reference, then it is refused")
    void given_aliasedAndUnaliasedGrants_should_beRefused() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants gr"
              + " WHERE gr.grant_resource = injcon.injector_contract_id) AND EXISTS (SELECT 1 FROM"
              + " grants, groups g WHERE grants.grant_group = g.group_id)";

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> grantsAliasesOf("SomeRepository#someGrantedQuery", sql));
    }

    @Test
    @DisplayName("given an unaliased grants in a comma list, then it is named by itself")
    void given_unaliasedGrantsInACommaList_should_beNamedByItself() {
      // Arrange
      String sql =
          "SELECT injcon.* FROM injectors_contracts injcon WHERE EXISTS (SELECT 1 FROM grants,"
              + " groups g WHERE grants.grant_resource = injcon.injector_contract_id)";

      // Act
      Set<String> aliases = grantsAliasesOf("SomeRepository#someGrantedQuery", sql);

      // Assert
      assertEquals(Set.of("grants"), aliases);
    }

    @Test
    @DisplayName("given a waiver spelling the grants alias in upper case, then it is accepted")
    void given_upperCaseWaiverAlias_should_beAccepted() {
      // Arrange
      Map<String, Set<String>> listed = Map.of("SomeRepository#someGrantedQuery", Set.of("gr"));

      // Act
      List<String> found =
          waiverViolations(
              "SomeRepository#someGrantedQuery injector_contract_id GR=injcon",
              REASON,
              Set.of(),
              listed);

      // Assert
      assertTrue(found.isEmpty(), "aliases compare without case. Got: " + found);
    }

    @Test
    @DisplayName("given two grant-reader waivers on one statement, then they are flagged")
    void given_twoGrantReaderWaiversOnOneStatement_should_beFlagged() {
      // Arrange
      Map<String, String> waivers =
          Map.of(
              KEY,
              REASON,
              "SomeRepository#someGrantedQuery attack_pattern_id gr=injconatt",
              REASON);

      // Act
      List<String> found = grantReaderWaiversBeyondOnePerStatement(waivers);

      // Assert
      assertTrue(
          found.stream().anyMatch(v -> v.contains("2 grant-reader waivers")),
          "one grants join per statement. Got: " + found);
    }
  }
}
