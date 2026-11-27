package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Builds the {@link TenantTables} the inspector filters against, from the live database schema:
 * every table carrying a {@code tenant_id} column is tenant-scoped, and the column's nullability is
 * its family (NOT NULL is strict, NULL also allows platform rows so it is dual-scope). The schema
 * is the source of truth so the set is complete: it covers join and link tables that carry a {@code
 * tenant_id} but have no entity class, which an entity-model scan would miss. Every one of them is
 * gated but the self-isolated tables ({@link TenantTables#selfIsolatedTables()}), so a new tenant
 * table is gated from the day it exists. The inspector itself is assembled from this dimension and
 * the marking one by {@link ScopeFilteringConfig}.
 */
@Slf4j
@AllowRawJdbc(reason = "reads information_schema metadata only; no tenant rows are accessed")
@Configuration
public class TenantFilteringConfig {

  /**
   * The rollout switch that named the gated tables. An install that sets it gets a warning at
   * startup and the same tables as any other install.
   */
  static final String RETIRED_ACTIVE_TABLES_PROPERTY = "openaev.tenant.active-tables";

  private static final String TENANT_TABLES_QUERY =
      "SELECT table_name, is_nullable FROM information_schema.columns "
          + "WHERE column_name = 'tenant_id' AND table_schema = current_schema()";

  /**
   * Tables every install has, strict and dual-scope: the first tables of the scenario, simulation,
   * inject and finding model and the role model, created by the earliest migrations and never
   * removed. The schema decides what is gated, so a derivation that reads the wrong schema, or runs
   * before Flyway, gates nothing and boots with no isolation; this check refuses to start instead.
   */
  private static final Set<String> ANCHOR_TABLES =
      Set.of("injects", "exercises", "scenarios", "findings", "roles");

  @Bean
  public TenantTables tenantTables(DataSource dataSource, Environment environment) {
    warnIfRetiredPropertySet(environment);
    return arm(deriveFromSchema(dataSource));
  }

  private static void warnIfRetiredPropertySet(Environment environment) {
    if (environment.containsProperty(RETIRED_ACTIVE_TABLES_PROPERTY)) {
      log.warn(
          "{} is ignored: the statement inspector gates every tenant table but the self-isolated"
              + " ones. Remove the property from the configuration.",
          RETIRED_ACTIVE_TABLES_PROPERTY);
    }
  }

  /**
   * Gates every tenant table of the schema but the self-isolated ones.
   *
   * @throws IllegalStateException when that leaves no table, or a set missing one of {@link
   *     #ANCHOR_TABLES}
   */
  static TenantTables arm(TenantTables schema) {
    TenantTables armed = schema.withoutSelfIsolated();
    Set<String> names = new HashSet<>(armed.strict());
    names.addAll(armed.dualScope());
    if (names.isEmpty()) {
      throw new IllegalStateException(
          "the schema yields no tenant table: wrong schema or search_path, or the tables were"
              + " derived before the migrations ran");
    }
    Set<String> missing = new TreeSet<>(ANCHOR_TABLES);
    missing.removeAll(names);
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "the schema lacks tables every install has: "
              + missing
              + ". Wrong schema or search_path, or the tables were derived before the"
              + " migrations ran");
    }
    return armed;
  }

  @Bean
  public TenantDimension tenantDimension(TenantTables tenantTables) {
    return new TenantDimension(tenantTables);
  }

  static TenantTables deriveFromSchema(DataSource dataSource) {
    Set<String> strict = new HashSet<>();
    Set<String> dualScope = new HashSet<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(TENANT_TABLES_QUERY);
        ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        String table = rows.getString("table_name");
        boolean nullable = "YES".equalsIgnoreCase(rows.getString("is_nullable"));
        (nullable ? dualScope : strict).add(table);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("cannot derive tenant tables from the schema", e);
    }
    return new TenantTables(strict, dualScope);
  }
}
