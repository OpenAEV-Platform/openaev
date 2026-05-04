package io.openaev.config;

import io.openaev.context.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.java.Log;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

/**
 * Wraps the auto-configured HikariCP {@link DataSource} so that every connection checked out has
 * the PostgreSQL session variable {@code app.current_tenant} set to the value from {@link
 * TenantContext}. This ensures Row-Level Security policies are enforced regardless of which
 * connection HikariCP returns.
 */
@Component
@Log
public class TenantAwareDataSourceConfig implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
      return new DelegatingDataSource(dataSource) {
        @Override
        public Connection getConnection() throws SQLException {
          Connection connection = super.getConnection();
          setTenantVariable(connection);
          return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
          Connection connection = super.getConnection(username, password);
          setTenantVariable(connection);
          return connection;
        }

        private void setTenantVariable(Connection connection) throws SQLException {
          if (TenantContext.isRlsBypassed()) {
            // Scheduled jobs need cross-tenant access — escalate to the DB owner
            // (superuser) which bypasses RLS policies.
            try (var stmt = connection.createStatement()) {
              stmt.execute("RESET ROLE");
            }
          } else {
            // Step 1: Set the non-superuser role so RLS policies are enforced.
            // The role may not exist yet during Flyway bootstrap — skip gracefully.
            if (!setRole(connection)) {
              return;
            }
            // Step 2: Set the tenant variable for RLS filtering.
            String tenantId = TenantContext.getCurrentTenant();
            try (PreparedStatement stmt =
                connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
              stmt.setString(1, tenantId);
              stmt.execute();
            }
          }
        }

        /**
         * Attempts to SET ROLE openaev_app on the connection.
         *
         * @return true if the role was set successfully, false if the role does not exist yet
         *     (Flyway bootstrap phase — no RLS policies exist either, so no data leak risk).
         */
        private boolean setRole(Connection connection) throws SQLException {
          try (var stmt = connection.createStatement()) {
            stmt.execute("SET ROLE openaev_app");
            return true;
          } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
              // Role not created yet (Flyway hasn't run the RLS migration).
              // Safe to skip: no RLS policies exist either.
              log.fine(
                  "Could not SET ROLE openaev_app (role may not exist yet): " + e.getMessage());
              return false;
            }
            // Unexpected failure (e.g. permission issue) — refuse to continue without RLS.
            throw new SQLException(
                "SET ROLE openaev_app failed unexpectedly. "
                    + "Refusing to proceed without RLS: "
                    + e.getMessage(),
                e);
          }
        }
      };
    }
    return bean;
  }
}
