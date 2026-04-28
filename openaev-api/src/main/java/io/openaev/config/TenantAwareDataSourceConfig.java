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
          String tenantId = TenantContext.getCurrentTenant();
          try (PreparedStatement stmt =
              connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            stmt.setString(1, tenantId);
            stmt.execute();
          }
        }
      };
    }
    return bean;
  }
}
