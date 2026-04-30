package io.openaev.utilstest;

import io.openaev.migration.TenantScopedTables;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Test listener that enables or disables PostgreSQL Row-Level Security on all tenant-scoped tables
 * at test startup, based on the property {@code openaev.rls.enabled}.
 *
 * <p>Usage: set {@code openaev.rls.enabled=false} in test properties (or via {@code
 * -Dopenaev.rls.enabled=false}) to disable RLS. When RLS is disabled, tenant isolation tests should
 * fail — proving they actually depend on RLS.
 *
 * <p>Default: {@code true} (RLS enabled).
 *
 * <p>This listener opens a direct JDBC connection (bypassing the pool) as the DB owner so it can
 * ALTER TABLE without being subject to SET ROLE or poisoning pool connections.
 */
@Slf4j
public class RlsToggleTestListener extends AbstractTestExecutionListener {

  private static volatile boolean applied = false;

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void beforeTestClass(TestContext testContext) throws Exception {
    if (applied) {
      return;
    }
    applied = true;

    ApplicationContext ctx = testContext.getApplicationContext();
    Environment env = ctx.getEnvironment();
    boolean rlsEnabled = !"false".equalsIgnoreCase(env.getProperty("openaev.rls.enabled"));

    String url = env.getProperty("spring.datasource.url");
    String username = env.getProperty("spring.datasource.username");
    String password = env.getProperty("spring.datasource.password");

    try (Connection connection = DriverManager.getConnection(url, username, password);
        Statement stmt = connection.createStatement()) {
      // Direct connection as DB owner (superuser/owner) — no SET ROLE applied
      for (String table : TenantScopedTables.TABLES) {
        if (rlsEnabled) {
          stmt.addBatch("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
          stmt.addBatch("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
        } else {
          stmt.addBatch("ALTER TABLE " + table + " DISABLE ROW LEVEL SECURITY");
        }
      }
      stmt.executeBatch();
    }

    if (rlsEnabled) {
      log.info("RLS is ENABLED for tests (openaev.rls.enabled=true)");
    } else {
      log.warn(
          "RLS is DISABLED for tests (openaev.rls.enabled=false) — "
              + "tenant isolation tests are expected to fail");
    }
  }
}
