package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.TenantBase;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

/**
 * Covers the tenant filtering wiring: tables derived from the live schema (every table with a
 * {@code tenant_id} column, classified by nullability, including join tables an entity scan would
 * miss), the inspector registered with Hibernate and inert by default, and the model/schema
 * coherence check that there is no gap versus what the entity markers (and so v1) protect.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Tenant filtering wiring and model/schema coherence")
class TenantFilteringConfigTest {

  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private ScopeStatementInspector inspector;

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

  @Test
  @DisplayName(
      "every tenant-marked entity is covered by the schema with the same family (no v1 hole)")
  void markedEntitiesAgreeWithSchema() {
    Set<Class<?>> marked =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .map(entity -> (Class<?>) entity.getJavaType())
            .filter(
                type ->
                    TenantBase.class.isAssignableFrom(type)
                        || DualScopeBase.class.isAssignableFrom(type))
            .collect(Collectors.toSet());

    TenantTables fromModel = TenantTables.fromEntities(marked);
    TenantTables fromSchema = TenantFilteringConfig.deriveFromSchema(dataSource);

    // A marked table missing here is either absent from the schema (tenant_id column forgotten) or
    // classified differently (marker says dual but the column is NOT NULL, or the reverse).
    Set<String> strictGap = new HashSet<>(fromModel.strict());
    strictGap.removeAll(fromSchema.strict());
    Set<String> dualGap = new HashSet<>(fromModel.dualScope());
    dualGap.removeAll(fromSchema.dualScope());

    assertTrue(
        strictGap.isEmpty(), "strict-marked entities not strict in the schema: " + strictGap);
    assertTrue(dualGap.isEmpty(), "dual-marked entities not dual in the schema: " + dualGap);

    // Guard against a weak pass: a near-empty metamodel enumeration would make the subset checks
    // trivially true. The model has ~38 tenant-aware entities across both families.
    assertTrue(
        marked.size() >= 20,
        "expected the metamodel to expose the tenant entities, got " + marked.size());
    assertTrue(fromModel.strict().contains("documents"), "sanity: a known strict table is present");
    assertTrue(fromModel.dualScope().contains("groups"), "sanity: a known dual table is present");
  }
}
