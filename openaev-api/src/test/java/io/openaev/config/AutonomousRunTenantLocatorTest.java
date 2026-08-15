package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Pins the read-only scope-bootstrap exemption of {@link AutonomousRunTenantLocator} and its
 * fail-closed contract.
 *
 * <p>The tenant-activation runbook treats raw JDBC on a tenant-active table as a hard blocker,
 * because it bypasses the statement inspector. The locator is the deliberate, narrow exception:
 * deriving a callback's scope FROM the run is a chicken-and-egg (the read that decides the scope
 * cannot run under the scope it is deciding, and the inspector would fail-close it), so it may
 * bypass the inspector ONLY while it stays a single-row, primary-key-addressed SELECT of the run's
 * own immutable {@code tenant_id}, filtered on the owning tenant's liveness (a soft-deleted tenant
 * is out of every caller scope, so the run-derived scope refuses it too). A reason that lives only
 * in a comment rots, so - like the insert-only seed exemption pinned by {@code
 * AttackPathSeedServiceTest} - it is enforced here: widening the statement, adding a second
 * statement, or adding a sibling bypass on the {@code autonomous_*} tables fails the build and
 * forces the conversation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("the run-tenant locator stays a pinned, fail-closed scope-bootstrap read")
class AutonomousRunTenantLocatorTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("the raw-JDBC exemption stays a single pinned PK read of tenant_id")
  void theRawJdbcExemptionStaysThePinnedScopeBootstrapRead() throws Exception {
    // Only the locator may bypass the inspector on the autonomous_* tables; a sibling bypass added
    // later must show up in this tree scan and be argued for explicitly.
    List<Path> bypasses = rawJdbcClassesTouchingAutonomousTables();
    assertThat(bypasses)
        .as("only the run-tenant locator may bypass the inspector on the autonomous_* tables")
        .extracting(path -> path.getFileName().toString())
        .containsExactly("AutonomousRunTenantLocator.java");

    // The exemption is read-only and minimal by definition: one statement, SELECT-only, projecting
    // the run's own tenant_id and addressing the row by primary key. Anything else must fail.
    assertThat(sqlLiterals(Files.readString(bypasses.get(0))))
        .as("the locator emits exactly one SQL statement")
        .containsExactly(AutonomousRunTenantLocator.SELECT_RUN_TENANT);
    assertThat(AutonomousRunTenantLocator.SELECT_RUN_TENANT)
        .isEqualTo(
            "SELECT r.tenant_id FROM autonomous_runs r JOIN tenants t ON t.tenant_id = r.tenant_id"
                + " WHERE r.autonomous_run_id = ? AND t.tenant_deleted_at IS NULL");
    // The liveness predicate is load-bearing, not decoration: a soft-deleted tenant is out of
    // every caller scope for its whole grace period, so the run-derived scope must fail closed on
    // it too instead of re-admitting a tenant no operator can reach. Pinned explicitly so a
    // rewrite of the statement cannot drop it without meeting this assertion by name.
    assertThat(AutonomousRunTenantLocator.SELECT_RUN_TENANT)
        .as("the bootstrap read must refuse a run whose owning tenant is soft-deleted")
        .contains("JOIN tenants")
        .contains("tenant_deleted_at IS NULL");
  }

  @Test
  @DisplayName("the literal scan counts every Java literal form, so no statement can hide")
  void theLiteralScanCountsEveryJavaLiteralForm() {
    // The pin above is only as strong as this scanner, so the scanner is pinned too: a statement
    // in a text block, one led by a CTE, one led by whitespace, and one split across a
    // +-concatenated chain (the formatter's long-string shape) must all be counted - the chain as
    // the single folded string - while a prose literal mentioning the run must not be.
    String sneakySource =
        """
        class Sneaky {
          static final String ONE_LINE = "SELECT a FROM b WHERE id = ?";
          static final String TEXT_BLOCK =
              \"""
              UPDATE autonomous_runs SET tenant_id = 'x' WHERE autonomous_run_id = ?
              \""";
          static final String CTE = "WITH last AS (SELECT 1) DELETE FROM autonomous_events";
          static final String PADDED = "  Truncate autonomous_directives";
          static final String CHAIN =
              "INSERT INTO autonomous_events (autonomous_event_id)"
                  + " VALUES (?)";
          static final String PROSE = "reads only the run's immutable tenant id";
        }
        """;

    assertThat(sqlLiterals(sneakySource))
        .as("both literal forms are counted, in any lead-in, chains folded, and prose is not")
        .containsExactlyInAnyOrder(
            "SELECT a FROM b WHERE id = ?",
            "UPDATE autonomous_runs SET tenant_id = 'x' WHERE autonomous_run_id = ?",
            "WITH last AS (SELECT 1) DELETE FROM autonomous_events",
            "Truncate autonomous_directives",
            "INSERT INTO autonomous_events (autonomous_event_id) VALUES (?)");
  }

  @Test
  @DisplayName("a known run resolves to its own tenant")
  void aKnownRunResolvesToItsOwnTenant() {
    AutonomousRunTenantLocator locator = new AutonomousRunTenantLocator(jdbcTemplate);
    when(jdbcTemplate.query(
            eq(AutonomousRunTenantLocator.SELECT_RUN_TENANT), any(RowMapper.class), eq("run-1")))
        .thenReturn(List.of("run-owner-tenant"));

    assertThat(locator.findRunTenant("run-1")).contains("run-owner-tenant");
  }

  @Test
  @DisplayName("an unknown run and a blank stored tenant are both fail-closed (empty)")
  void unknownRunAndBlankTenantAreFailClosed() {
    AutonomousRunTenantLocator locator = new AutonomousRunTenantLocator(jdbcTemplate);
    when(jdbcTemplate.query(
            eq(AutonomousRunTenantLocator.SELECT_RUN_TENANT), any(RowMapper.class), eq("ghost")))
        .thenReturn(List.of());
    when(jdbcTemplate.query(
            eq(AutonomousRunTenantLocator.SELECT_RUN_TENANT), any(RowMapper.class), eq("blank")))
        .thenReturn(List.of(" "));

    assertThat(locator.findRunTenant("ghost")).isEmpty();
    assertThat(locator.findRunTenant("blank")).isEmpty();
  }

  @Test
  @DisplayName("a null or blank run id never reaches the database")
  void nullOrBlankRunIdNeverReachesTheDatabase() {
    AutonomousRunTenantLocator locator = new AutonomousRunTenantLocator(jdbcTemplate);

    assertThat(locator.findRunTenant(null)).isEmpty();
    assertThat(locator.findRunTenant("  ")).isEmpty();
    verifyNoInteractions(jdbcTemplate);
  }

  /**
   * Every production class, in EVERY production module, that opts out of the inspector AND names an
   * autonomous table. Scanning the trees rather than the one known file is the point: a new sibling
   * bypass must show up here instead of slipping past a test that only ever looked at the locator -
   * and {@code openaev-model} already hosts {@code @AllowRawJdbc} classes of its own, so a
   * model-layer bypass must fail this pin exactly like an api-layer one.
   */
  private static List<Path> rawJdbcClassesTouchingAutonomousTables() throws Exception {
    // Surefire runs with the module directory as CWD, so sibling production roots sit one level up.
    List<Path> roots =
        Stream.of(
                Path.of("src/main/java"),
                Path.of("../openaev-model/src/main/java"),
                Path.of("../openaev-framework/src/main/java"))
            .filter(Files::isDirectory)
            .toList();
    assertThat(roots)
        .as(
            "the api and model production source roots must both be scannable, or this pin is blind")
        .hasSizeGreaterThanOrEqualTo(2);
    List<Path> bypasses = new ArrayList<>();
    for (Path root : roots) {
      try (Stream<Path> tree = Files.walk(root)) {
        tree.filter(path -> path.toString().endsWith(".java"))
            .filter(
                path -> {
                  String body = read(path);
                  return body.contains("@AllowRawJdbc") && body.contains("autonomous_");
                })
            .forEach(bypasses::add);
      }
    }
    return bypasses;
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception e) {
      throw new IllegalStateException("cannot read " + path, e);
    }
  }

  /** Matches a SQL verb appearing as a standalone word anywhere inside a literal. */
  private static final Pattern SQL_VERB =
      Pattern.compile(
          "\\b(?:INSERT|SELECT|UPDATE|DELETE|TRUNCATE|MERGE)\\b", Pattern.CASE_INSENSITIVE);

  /**
   * Every string literal the source declares, in BOTH Java literal forms (one-line and text block),
   * that carries a SQL verb anywhere in its content. Anchoring on a verb right after the opening
   * quote would miss a text block (its opening delimiter is always followed by a line terminator,
   * never by the verb) and any statement led by whitespace or a CTE, so a second statement could
   * hide from the pin; containing-verb matching over every literal form keeps the exact-statement
   * claim honest. A chain of {@code +}-concatenated one-line literals is folded into the single
   * string javac constant-folds it to, so the formatter splitting a long statement across fragments
   * neither hides a verb-less fragment from the pin nor fails it spuriously. {@link
   * #theLiteralScanCountsEveryJavaLiteralForm()} pins this scanner itself.
   */
  private static List<String> sqlLiterals(String source) {
    // A unicode-escaped delimiter (\u0022) is the one remaining way to hide a literal from a
    // raw-text scan, and the locator has no legitimate use for unicode escapes at all.
    assertThat(source)
        .as("unicode escapes could hide a literal from this scan")
        .doesNotContain("\\u");
    List<String> found = new ArrayList<>();
    // Text blocks are consumed first and excised, so their bodies (which may contain quote
    // characters) can never derail the one-line pass over the remainder.
    Matcher textBlocks = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL).matcher(source);
    StringBuilder remainder = new StringBuilder();
    int copiedUpTo = 0;
    while (textBlocks.find()) {
      collectWhenSql(found, textBlocks.group(1));
      remainder.append(source, copiedUpTo, textBlocks.start());
      copiedUpTo = textBlocks.end();
    }
    remainder.append(source, copiedUpTo, source.length());
    Matcher oneLiners =
        Pattern.compile("\"[^\"\\n]*\"(?:\\s*\\+\\s*\"[^\"\\n]*\")*").matcher(remainder);
    while (oneLiners.find()) {
      collectWhenSql(found, joinLiteralChain(oneLiners.group()));
    }
    assertThat(found)
        .as("the scan must find the locator's statement, or it proves nothing")
        .isNotEmpty();
    return found;
  }

  /**
   * Folds a chain of {@code +}-concatenated one-line literals into the single string javac
   * constant-folds it to. Only directly adjacent literals join: a chain broken by any non-literal
   * operand keeps its fragments separate, so dynamically composed SQL still fails the pin loudly.
   */
  private static String joinLiteralChain(String chain) {
    StringBuilder joined = new StringBuilder();
    Matcher segments = Pattern.compile("\"([^\"\\n]*)\"").matcher(chain);
    while (segments.find()) {
      joined.append(segments.group(1));
    }
    return joined.toString();
  }

  private static void collectWhenSql(List<String> found, String literal) {
    if (SQL_VERB.matcher(literal).find()) {
      found.add(literal.strip());
    }
  }
}
