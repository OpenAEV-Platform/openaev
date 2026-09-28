package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * narrowed to the activation allowlist ({@code openaev.tenant.active-tables}), empty by default, so
 * the inspector stays inert until a table is onboarded. The inspector itself is assembled from this
 * dimension and the marking one by {@link ScopeFilteringConfig}.
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
    return deriveFromSchema(dataSource).restrictTo(allowlist);
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
