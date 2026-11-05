package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.architecture.SharedTenantKeyJoinArchTest.SqlRun;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every statement that reads or writes {@code grants} is listed, with what makes it correct.
 *
 * <p>A grant applies in the tenant of the group that holds it, and in every tenant when that group
 * is a platform group. {@code grants} has no tenant column, so the rule lives in each reader: one
 * that resolves grants on a resource id repeating across tenants must restrict the group to the
 * request's tenant or to the platform, as {@code User.getGrants} does in memory. A new reader is
 * therefore a diff a reviewer must see, and a reader listed as tenant-aware must carry the
 * predicate.
 *
 * <p>What this adds to {@link SharedTenantKeyJoinArchTest}: that guard judges joins between two
 * tenant-carrying aliases on a shared column, so it cannot see a grant's tenant at all (it is the
 * group's), and it never reads JPQL property paths or Criteria code. This one lists every reader
 * whatever its shape: SQL and JPQL literals, and Criteria roots or joins on {@code Grant}.
 *
 * <p>Shapes this scan cannot see: in-memory navigation of a loaded collection ({@code
 * Group.getGrants()}, {@code GrantableBase.getGrants()}), Spring Data derived and inherited
 * repository methods, and fetch plans declared by annotation. The in-memory reads of a user's
 * grants go through {@code User.scopedGroups()}.
 */
@DisplayName("Only listed statements read grants, and the tenant-aware ones carry the group tenant")
class GrantReaderAllowlistArchTest {

  private static final String ALLOWLIST_RESOURCE = "/grant-readers.txt";

  /**
   * Frozen: a reader removed from the code takes its line out of the list and this constant down in
   * the same change, so the list cannot keep a free slot for a new reader to take.
   */
  private static final int ALLOWLIST_SIZE = 21;

  private static final String GROUP_TENANT = "group-tenant";

  private static final List<String> CLASSIFICATIONS =
      List.of(GROUP_TENANT, "unique-id", "write", "dead-code");

  /** {@code FROM Grant}, {@code JOIN grants}, {@code JOIN e.grants}, {@code UPDATE Grant}. */
  private static final Pattern GRANT_STATEMENT =
      Pattern.compile(
          "\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(?:FETCH\\s+)?(?:[A-Za-z_]\\w*\\.)?grants?\\b",
          Pattern.CASE_INSENSITIVE);

  /** A Criteria root, join or specification on {@code Grant}. */
  private static final Pattern GRANT_CRITERIA =
      Pattern.compile(
          "\\b(?:from|join|fetch)\\s*\\(\\s*Grant\\.class"
              + "|\\.(?:join|get|fetch)\\s*\\(\\s*\"grants\""
              + "|\\bSpecification\\s*<\\s*Grant\\s*>");

  /** {@code gr.tenant IS NULL OR gr.tenant.id = ...} in JPQL, the same on tenant_id natively. */
  private static final Pattern GROUP_TENANT_SQL =
      Pattern.compile(
          "\\b(\\w+)\\.tenant(?:_id)?\\s+IS\\s+NULL\\s+OR\\s+\\1\\.tenant(?:_id|\\.id)\\s*=",
          Pattern.CASE_INSENSITIVE);

  /** The Criteria form of the same rule, shared by every Criteria reader that needs it. */
  private static final Pattern GROUP_TENANT_CRITERIA =
      Pattern.compile("\\bgroupCountsInTenant\\s*\\(");

  private static final Pattern ENTRY_LINE = Pattern.compile("(\\S+)\\s+#\\s*(.*)");

  /** One place a statement on grants is written: its owner and where it sits. */
  private record Reader(String origin, String location, String text) {}

  @Test
  @DisplayName("every statement on grants is listed, and every listed one still exists")
  void everyGrantReaderIsListed() throws IOException {
    // Arrange
    Map<String, List<Reader>> found = readers();
    Map<String, String> listed = allowlist();

    // Act
    List<String> unlisted = new ArrayList<>();
    found.forEach(
        (origin, readers) -> {
          if (!listed.containsKey(origin)) {
            unlisted.add(origin + "  (" + readers.getFirst().location() + ")");
          }
        });
    List<String> stale = new ArrayList<>(listed.keySet());
    stale.removeAll(found.keySet());

    // Assert
    System.out.println("[grant-readers] found=" + found.size() + " listed=" + listed.size());
    assertThat(found.keySet())
        .as("a reader whose owner cannot be resolved cannot be listed")
        .noneMatch(origin -> origin.endsWith("#unknown"));
    assertTrue(
        unlisted.isEmpty(),
        "these statements read or write grants and are not in "
            + ALLOWLIST_RESOURCE
            + ". A grant applies in the tenant of the group that holds it: list the reader with"
            + " the classification that makes it correct, and restrict the group to the request's"
            + " tenant or the platform if it can match an id shared across tenants:\n  "
            + String.join("\n  ", unlisted));
    assertTrue(
        stale.isEmpty(),
        "these entries match no statement any more. Remove them in the change that removed the"
            + " reader:\n  "
            + String.join("\n  ", stale));
  }

  @Test
  @DisplayName("every reader listed as group-tenant restricts the group to the tenant")
  void everyGroupTenantReaderCarriesThePredicate() throws IOException {
    // Arrange
    Map<String, List<Reader>> found = readers();
    Map<String, String> listed = allowlist();

    // Act
    List<String> missing = new ArrayList<>();
    listed.forEach(
        (origin, reason) -> {
          if (reason.startsWith(GROUP_TENANT) && found.containsKey(origin)) {
            for (Reader reader : found.get(origin)) {
              if (!GROUP_TENANT_SQL.matcher(reader.text()).find()
                  && !GROUP_TENANT_CRITERIA.matcher(reader.text()).find()) {
                missing.add(origin + "  (" + reader.location() + ")");
              }
            }
          }
        });

    // Assert
    assertTrue(
        missing.isEmpty(),
        "these readers are listed as group-tenant but their statement does not restrict the"
            + " grant's group to a tenant or the platform:\n  "
            + String.join("\n  ", missing));
  }

  @Test
  @DisplayName("the list is frozen at its size and every entry carries a classification")
  void theAllowlistIsFrozenAndClassified() throws IOException {
    // Arrange
    Map<String, String> listed = allowlist();

    // Act
    List<String> unclassified =
        listed.entrySet().stream()
            .filter(
                entry ->
                    CLASSIFICATIONS.stream().noneMatch(c -> entry.getValue().startsWith(c + " : ")))
            .map(Map.Entry::getKey)
            .toList();

    // Assert
    assertThat(listed).hasSize(ALLOWLIST_SIZE);
    assertThat(unclassified)
        .as("entries must start their reason with one of %s followed by ' : '", CLASSIFICATIONS)
        .isEmpty();
  }

  @Nested
  @DisplayName("The scan cannot pass vacuously")
  class NonVacuity {

    @Test
    @DisplayName("given each statement shape on grants, when matched, then each is recognised")
    void given_eachGrantStatementShape_should_beRecognised() {
      // Arrange
      List<String> statements =
          List.of(
              "SELECT g FROM Grant g JOIN g.group gr",
              "SELECT 1 FROM groups g INNER JOIN grants gr ON g.group_id = gr.grant_group",
              "select e from Exercise e join e.grants as grant join grant.group",
              "UPDATE Grant g SET g.resourceId = :id");

      // Act / Assert
      statements.forEach(
          sql -> assertTrue(GRANT_STATEMENT.matcher(sql).find(), "not recognised: " + sql));
      assertTrue(
          !GRANT_STATEMENT.matcher("SELECT grant_resource FROM grant_audit").find(),
          "a column or another table must not be read as grants");
    }

    @Test
    @DisplayName("given each Criteria shape on Grant, when matched, then each is recognised")
    void given_eachGrantCriteriaShape_should_beRecognised() {
      // Arrange
      List<String> code =
          List.of(
              "Root<Grant> grantTable = accessibleResources.from(Grant.class);",
              "Path<Object> path = root.join(\"grants\").join(\"group\");",
              "Specification<Grant> byGroup = (root, query, cb) -> null;");

      // Act / Assert
      code.forEach(
          line -> assertTrue(GRANT_CRITERIA.matcher(line).find(), "not recognised: " + line));
    }

    @Test
    @DisplayName(
        "given a group tenant predicate on another alias, when matched, then it is refused")
    void given_groupTenantPredicateOnTwoAliases_should_beRefused() {
      // Arrange: the IS NULL and the equality must name the same group.
      String split = "AND (a.tenant IS NULL OR b.tenant.id = :tenantId)";
      String joined = "AND (gr.tenant IS NULL OR gr.tenant.id = :tenantId)";
      String nativeForm = "AND (g.tenant_id IS NULL OR g.tenant_id = injcon.tenant_id)";

      // Act / Assert
      assertTrue(!GROUP_TENANT_SQL.matcher(split).find(), "two aliases must not pass");
      assertTrue(GROUP_TENANT_SQL.matcher(joined).find(), "the JPQL form must pass");
      assertTrue(GROUP_TENANT_SQL.matcher(nativeForm).find(), "the native form must pass");
    }

    @Test
    @DisplayName("the scan finds readers of both shapes in the production sources")
    void theScanFindsBothShapes() throws IOException {
      // Act
      Map<String, List<Reader>> found = readers();

      // Assert
      assertThat(found.keySet())
          .contains(
              "GrantRepository#existsByUserIdAndGroupTenantAndResourceIdAndNameIn",
              "InjectorContractRepository#getAllRawInjectorsContractsGranted",
              "SpecificationUtils#accessibleResourcesSubquery");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The scan
  // ---------------------------------------------------------------------------------------------

  /** Every reader, keyed by owner, from the SQL corpus and from Criteria code. */
  private static Map<String, List<Reader>> readers() throws IOException {
    Map<String, List<Reader>> found = new TreeMap<>();
    for (SqlRun run : SharedTenantKeyJoinArchTest.sqlCorpus()) {
      if (GRANT_STATEMENT.matcher(run.sql()).find()) {
        found
            .computeIfAbsent(run.origin(), key -> new ArrayList<>())
            .add(new Reader(run.origin(), run.file() + ":" + run.line(), run.sql()));
      }
    }
    for (Path root : SharedTenantKeyJoinArchTest.productionRoots()) {
      try (Stream<Path> sources = Files.walk(root)) {
        for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
          if (source.toString().contains("/migration/")) {
            continue;
          }
          criteriaReaders(source)
              .forEach(
                  reader ->
                      found.computeIfAbsent(reader.origin(), key -> new ArrayList<>()).add(reader));
        }
      }
    }
    return found;
  }

  /**
   * Criteria readers, owned by their method. The owner is resolved from the end of the matched
   * line, so a match in a method's own signature ({@code Specification<Grant> name(}) belongs to
   * that method. The judged text runs from the match to the next member, which is where a Criteria
   * reader writes its predicates.
   */
  private static List<Reader> criteriaReaders(Path source) throws IOException {
    String text = withoutComments(Files.readString(source));
    String className = source.getFileName().toString().replace(".java", "");
    List<Reader> readers = new ArrayList<>();
    Matcher match = GRANT_CRITERIA.matcher(text);
    while (match.find()) {
      int lineEnd = text.indexOf('\n', match.end());
      lineEnd = lineEnd < 0 ? text.length() : lineEnd;
      String method = SharedTenantKeyJoinArchTest.owningMethod(text, lineEnd, lineEnd);
      int line = (int) text.substring(0, match.start()).chars().filter(c -> c == '\n').count() + 1;
      readers.add(
          new Reader(
              className + "#" + method,
              SharedTenantKeyJoinArchTest.relative(source) + ":" + line,
              text.substring(match.start(), methodEnd(text, match.end()))));
    }
    return readers;
  }

  private static final Pattern NEXT_MEMBER =
      Pattern.compile("(?m)^  (?:public|protected|private|static|@)");

  private static int methodEnd(String text, int from) {
    Matcher next = NEXT_MEMBER.matcher(text);
    return next.find(from) ? next.start() : text.length();
  }

  /** Comments blanked to spaces, strings kept, so offsets and line numbers stay true. */
  private static String withoutComments(String text) {
    StringBuilder out = new StringBuilder(text);
    int index = 0;
    while (index < text.length()) {
      char c = text.charAt(index);
      if (c == '"' || c == '\'') {
        index = skipLiteral(text, index, c);
      } else if (text.startsWith("//", index)) {
        int end = text.indexOf('\n', index);
        end = end < 0 ? text.length() : end;
        blank(out, index, end);
        index = end;
      } else if (text.startsWith("/*", index)) {
        int end = text.indexOf("*/", index + 2);
        end = end < 0 ? text.length() : end + 2;
        blank(out, index, end);
        index = end;
      } else {
        index++;
      }
    }
    return out.toString();
  }

  private static int skipLiteral(String text, int index, char quote) {
    if (quote == '"' && text.startsWith("\"\"\"", index)) {
      int end = text.indexOf("\"\"\"", index + 3);
      return end < 0 ? text.length() : end + 3;
    }
    int cursor = index + 1;
    while (cursor < text.length() && text.charAt(cursor) != quote) {
      cursor += text.charAt(cursor) == '\\' ? 2 : 1;
    }
    return Math.min(cursor + 1, text.length());
  }

  private static void blank(StringBuilder out, int from, int to) {
    for (int i = from; i < to; i++) {
      if (out.charAt(i) != '\n') {
        out.setCharAt(i, ' ');
      }
    }
  }

  /** The owners of the statements listed as group-tenant, for guards that defer to this one. */
  static Set<String> groupTenantReaders() throws IOException {
    return allowlist().entrySet().stream()
        .filter(entry -> entry.getValue().startsWith(GROUP_TENANT))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private static Map<String, String> allowlist() throws IOException {
    Map<String, String> entries = new LinkedHashMap<>();
    try (InputStream in =
        GrantReaderAllowlistArchTest.class.getResourceAsStream(ALLOWLIST_RESOURCE)) {
      assertTrue(in != null, ALLOWLIST_RESOURCE + " is missing from the test classpath");
      for (String raw : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        Matcher entry = ENTRY_LINE.matcher(line);
        assertTrue(
            entry.matches(), "malformed entry, expected '<Class>#<method> # <reason>': " + line);
        assertTrue(
            entries.put(entry.group(1), entry.group(2).strip()) == null,
            "duplicate entry: " + entry.group(1));
      }
    }
    return entries;
  }
}
