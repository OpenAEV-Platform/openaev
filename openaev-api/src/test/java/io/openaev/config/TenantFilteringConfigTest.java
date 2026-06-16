package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import javax.sql.DataSource;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

/**
 * Verifies that the tenant tables are derived from the live schema: every table with a {@code
 * tenant_id} column, classified by the column's nullability, including the join tables an
 * entity-model scan would miss.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("TenantFilteringConfig.deriveFromSchema")
class TenantFilteringConfigTest {

  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private TenantStatementInspector inspector;

  @Test
  @DisplayName("classifies tenant tables by the nullability of their tenant_id column")
  void classifiesByNullability() {
    TenantTables tables = TenantFilteringConfig.deriveFromSchema(dataSource);
    assertEquals(TenantTables.Family.STRICT, tables.family("documents"), "NOT NULL tenant_id");
    assertEquals(TenantTables.Family.DUAL, tables.family("groups"), "nullable tenant_id");
    assertEquals(TenantTables.Family.DUAL, tables.family("parameters"), "nullable tenant_id");
  }

  @Test
  @DisplayName("covers join tables that carry a tenant_id but have no entity class")
  void coversJoinTablesWithoutEntities() {
    TenantTables tables = TenantFilteringConfig.deriveFromSchema(dataSource);
    assertEquals(TenantTables.Family.STRICT, tables.family("users_tenants"));
    assertEquals(TenantTables.Family.STRICT, tables.family("injectors_contracts_attack_patterns"));
  }

  @Test
  @DisplayName("a table without a tenant_id column is not in the derived set")
  void nonTenantTableExcluded() {
    assertEquals(
        TenantTables.Family.NONE,
        TenantFilteringConfig.deriveFromSchema(dataSource).family("users"));
  }

  @Test
  @DisplayName("an empty allowlist leaves the derived set inert")
  void emptyAllowlistIsInert() {
    TenantTables active = TenantFilteringConfig.deriveFromSchema(dataSource).restrictTo(List.of());
    assertTrue(active.strict().isEmpty());
    assertTrue(active.dualScope().isEmpty());
  }

  @Test
  @DisplayName("the inspector is the one Hibernate runs (guards against another one displacing it)")
  void inspectorIsRegisteredWithHibernate() {
    // Guards the putIfAbsent trade-off: if any other statement_inspector were ever installed ahead
    // of ours, isolation would silently switch off. This assertion fails the build instead.
    assertSame(
        inspector,
        entityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getSessionFactoryOptions()
            .getStatementInspector());
  }

  @Test
  @DisplayName("with no active table the wired inspector passes statements through untouched")
  void wiredInspectorIsInertByDefault() {
    String sql = "SELECT * FROM documents d WHERE d.id = ?";
    assertEquals(sql, inspector.inspect(sql));
  }
}
