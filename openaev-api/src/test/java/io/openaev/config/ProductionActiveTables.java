package io.openaev.config;

import io.openaev.architecture.BackgroundEntrypointTenantScopeArchTest;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The tenant tables activated in production, read from {@code application.properties} rather than
 * from the running context: the test classpath declares no activation list, so a context-based read
 * reports none active and every assertion built on it is vacuous.
 *
 * <p>This read is the one fact a dozen test classes need and each used to parse for itself. Two
 * readers of the same list drift, and a drifted reader fails open: it sees fewer active tables than
 * production has, so the guard built on it stops guarding without failing. One reader, one answer.
 *
 * <p>The answer is the tables armed, not the property's tokens: under the terminal {@code *} it is
 * the tables {@code *} arms ({@link #armedBy}). That expansion comes from the background guard's
 * entity scan, which is loaded only when the list is {@code *}.
 *
 * <p>Not to be confused with {@link TenantTables}, which answers a different question: given a set
 * of entities, which tables are strict tenant tables and which are dual-scope. That classification
 * says nothing about which of them production has switched on.
 */
public final class ProductionActiveTables {

  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";
  private static final String KEY = "openaev.tenant.active-tables";

  private ProductionActiveTables() {}

  /**
   * The tables production arms, lowercased and sorted: the production activation list read through
   * {@link #armedBy}, so the terminal {@code *} comes back as the tables it arms.
   */
  public static Set<String> names() {
    Properties properties = new Properties();
    try (InputStream in = new FileInputStream(APPLICATION_PROPERTIES)) {
      properties.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + APPLICATION_PROPERTIES, e);
    }
    return armedBy(properties.getProperty(KEY, ""));
  }

  /**
   * The tables an activation list arms, lowercased and sorted. An explicit list arms what it names.
   * The terminal {@value TenantTables#ALL_STRICT} arms every strict table but the self-isolated
   * ones, so it is returned expanded, the way {@link TenantTables#restrictTo} resolves it, and
   * never as the token itself: a caller comparing table names, or adding its own tables on top,
   * then reads and registers the tables the statement inspector gates in production. A {@code *}
   * beside other tables is refused, as production refuses it at startup.
   *
   * <p>That is the inspector's table set, not the write-attribution check's. Under {@code *},
   * {@code TenantBaseListener} refuses an unattributed write on every entity it listens to, the
   * self-isolated ones included, while an expanded list leaves those out and cannot name them:
   * naming a self-isolated table in an explicit list is a configuration error. So a test that
   * registers this expansion under a production {@code *} has the inspector production has and a
   * narrower unattributed-write check on the self-isolated entities.
   */
  public static Set<String> armedBy(String value) {
    Set<String> tokens =
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(TreeSet::new));
    if (!tokens.contains(TenantTables.ALL_STRICT)) {
      return tokens;
    }
    if (tokens.size() > 1) {
      throw new IllegalArgumentException(
          "active-tables is either '"
              + TenantTables.ALL_STRICT
              + "' or a list of tables, not both: "
              + tokens);
    }
    return new TreeSet<>(BackgroundEntrypointTenantScopeArchTest.wildcardActivatedTables());
  }

  /** Whether production has switched this table on. */
  public static boolean contains(String table) {
    return names().contains(table.toLowerCase(Locale.ROOT));
  }
}
