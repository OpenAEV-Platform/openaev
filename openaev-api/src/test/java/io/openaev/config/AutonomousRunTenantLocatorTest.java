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
 * own immutable {@code tenant_id}. A reason that lives only in a comment rots, so - like the
 * insert-only seed exemption pinned by {@code AttackPathSeedServiceTest} - it is enforced here:
 * widening the statement, adding a second statement, or adding a sibling bypass on the {@code
 * autonomous_*} tables fails the build and forces the conversation.
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
        .isEqualTo("SELECT tenant_id FROM autonomous_runs WHERE autonomous_run_id = ?");
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

  /** The full SQL string literals the source declares, recognised by their leading verb. */
  private static List<String> sqlLiterals(String source) {
    Matcher matcher =
        Pattern.compile(
                "\"((?:INSERT|SELECT|UPDATE|DELETE|TRUNCATE|MERGE)\\b[^\"]*)\"",
                Pattern.CASE_INSENSITIVE)
            .matcher(source);
    List<String> found = new ArrayList<>();
    while (matcher.find()) {
      found.add(matcher.group(1));
    }
    assertThat(found)
        .as("the scan must find the locator's statement, or it proves nothing")
        .isNotEmpty();
    return found;
  }
}
