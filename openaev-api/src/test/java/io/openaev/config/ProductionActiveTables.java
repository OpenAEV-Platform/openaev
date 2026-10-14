package io.openaev.config;

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
 * <p>Not to be confused with {@link TenantTables}, which answers a different question: given a set
 * of entities, which tables are strict tenant tables and which are dual-scope. That classification
 * says nothing about which of them production has switched on.
 */
public final class ProductionActiveTables {

  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";
  private static final String KEY = "openaev.tenant.active-tables";

  private ProductionActiveTables() {}

  /** The table names in the production activation list, lowercased and sorted. */
  public static Set<String> names() {
    Properties properties = new Properties();
    try (InputStream in = new FileInputStream(APPLICATION_PROPERTIES)) {
      properties.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + APPLICATION_PROPERTIES, e);
    }
    return Arrays.stream(properties.getProperty(KEY, "").split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** Whether production has switched this table on. */
  public static boolean contains(String table) {
    return names().contains(table.toLowerCase(Locale.ROOT));
  }
}
