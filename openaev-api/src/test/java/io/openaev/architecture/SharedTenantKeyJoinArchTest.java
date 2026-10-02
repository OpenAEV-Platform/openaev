package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

  /**
   * The classifications a waiver may carry. A bare entry, or one whose reason starts with anything
   * else, is rejected: an entry must say what makes the join safe, not merely that it is.
   */
  private static final List<String> CLASSIFICATIONS =
      List.of("reaches-active-table:", "dead-code", "not-established", "fixed-pending:");

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
  private record SqlRun(String file, String className, String method, int line, String sql) {
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
    Set<String> scannedPredicates = new TreeSet<>();
    for (SqlRun run : scanned) {
      scannedPredicates.addAll(predicates(run.sql()));
    }
    Set<String> missed = new TreeSet<>();
    for (String sql : declared) {
      for (String predicate : predicates(sql)) {
        if (!scannedPredicates.contains(predicate)) {
          missed.add(predicate);
        }
      }
    }
    assertTrue(
        missed.isEmpty(),
        "the source scan does not see these alias-to-alias predicates that the classpath declares,"
            + " so a query the guard must judge is invisible to it:\n  "
            + String.join("\n  ", missed));
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

  @Test
  @DisplayName("every waiver carries a classification and a reason")
  void everyWaiverCarriesAClassificationAndAReason() throws IOException {
    List<String> malformed = new ArrayList<>();
    for (Map.Entry<String, String> waiver : waivers().entrySet()) {
      String reason = waiver.getValue();
      if (reason.isBlank()) {
        malformed.add(waiver.getKey() + " :: no reason at all");
        continue;
      }
      if (CLASSIFICATIONS.stream().noneMatch(reason::startsWith)) {
        malformed.add(
            waiver.getKey() + " :: reason does not begin with a classification: " + reason);
        continue;
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
        malformed.add(waiver.getKey() + " :: classification with no reason behind it: " + reason);
      }
      if (reason.startsWith("not-established") && !reason.contains("until-active:")) {
        malformed.add(
            waiver.getKey()
                + " :: an unestablished waiver must name the table whose activation retires it,"
                + " as until-active:<table>");
      }
      if (reason.startsWith("reaches-active-table:")
          && reason.substring("reaches-active-table:".length()).split("[ ,:]")[0].isBlank()) {
        malformed.add(waiver.getKey() + " :: reaches-active-table must name the table");
      }
      if (reason.startsWith("fixed-pending:")
          && !reason.substring("fixed-pending:".length()).matches("#\\d+\\b.*")) {
        malformed.add(
            waiver.getKey()
                + " :: fixed-pending must name the pull request that repairs it, as"
                + " fixed-pending:#<number>");
      }
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
  private static List<Path> productionRoots() throws IOException {
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

  private static List<SqlRun> sqlCorpus() throws IOException {
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
  private static String owningMethod(String text, int start, int end) {
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

  private static String relative(Path source) {
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
}
