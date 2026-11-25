package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link TenantTables} the inspector filters against, from the live database schema:
 * every table carrying a {@code tenant_id} column is tenant-scoped, and the column's nullability is
 * its family (NOT NULL is strict, NULL also allows platform rows so it is dual-scope). The schema
 * is the source of truth so the set is complete: it covers join and link tables that carry a {@code
 * tenant_id} but have no entity class, which an entity-model scan would miss. The set is then
 * narrowed to the activation allowlist ({@code openaev.tenant.active-tables}): empty by default, so
 * the inspector is inert, or {@code *}, which arms every tenant table. The inspector itself is
 * assembled from this dimension and the marking one by {@link ScopeFilteringConfig}.
 */
@AllowRawJdbc(reason = "reads information_schema metadata only; no tenant rows are accessed")
@Configuration
public class TenantFilteringConfig {

  private static final String TENANT_TABLES_QUERY =
      "SELECT table_name, is_nullable FROM information_schema.columns "
          + "WHERE column_name = 'tenant_id' AND table_schema = current_schema()";

  @Bean
  public TenantTables tenantTables(
      DataSource dataSource, @Value("${openaev.tenant.active-tables:}") List<String> activeTables) {
    List<String> allowlist = activeTables.stream().filter(name -> !name.isBlank()).toList();
    return arm(deriveFromSchema(dataSource), allowlist);
  }

  /**
   * Tables every install has, strict and dual-scope: the first tables of the scenario, simulation,
   * inject and finding model and the role model, created by the earliest migrations and never
   * removed. Under {@code *} the schema decides what is armed, so a derivation that reads the wrong
   * schema, or runs before Flyway, arms nothing and boots with no isolation. An explicit list
   * refuses to start in that case (a name it cannot find is unknown); this check gives {@code *}
   * the same refusal.
   */
  private static final Set<String> WILDCARD_ANCHOR_TABLES =
      Set.of("injects", "exercises", "scenarios", "findings", "roles");

  /**
   * Arms the tables an allowlist names. The wildcard also refuses to start when it resolves to no
   * table, or to a set missing one of {@link #WILDCARD_ANCHOR_TABLES}.
   */
  static TenantTables arm(TenantTables schema, List<String> allowlist) {
    TenantTables armed = schema.restrictTo(allowlist);
    if (allowlist.contains(TenantTables.ALL_STRICT)) {
      Set<String> names = new HashSet<>(armed.strict());
      names.addAll(armed.dualScope());
      if (names.isEmpty()) {
        throw new IllegalStateException(
            "active-tables is '*' but the schema yields no tenant table: wrong schema or"
                + " search_path, or the tables were derived before the migrations ran");
      }
      Set<String> missing = new TreeSet<>(WILDCARD_ANCHOR_TABLES);
      missing.removeAll(names);
      if (!missing.isEmpty()) {
        throw new IllegalStateException(
            "active-tables is '*' but the schema lacks tables every install has: "
                + missing
                + ". Wrong schema or search_path, or the tables were derived before the"
                + " migrations ran");
      }
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
