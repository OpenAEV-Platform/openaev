package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Readiness proof for the first table chosen for activation: {@code import_mappers}. It was picked
 * because it is strict tenant-scoped, has no native repository queries that could fail to parse,
 * has no JDBC bypass, and is reached only through HTTP flows (CRUD via the mapper API and
 * HTTP-triggered imports), never a background job. With the table activated, the transaction scope
 * alone decides which tenant's mappers a query sees and which a write can touch.
 */
@SpringBootTest(properties = "openaev.tenant.active-tables=import_mappers")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("First table activation readiness: import_mappers")
class ImportMapperTenantIsolationTest {

  private static final String TENANT_A = "mapper-tenant-a";
  private static final String TENANT_B = "mapper-tenant-b";
  private static final String MAPPER_A = "mapper-of-a";
  private static final String MAPPER_B = "mapper-of-b";

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() {
    insertTenant(TENANT_A);
    insertTenant(TENANT_B);
    insertMapper(MAPPER_A, TENANT_A);
    insertMapper(MAPPER_B, TENANT_B);
  }

  @Test
  @DisplayName("the active scope alone decides which tenant's mappers a query returns")
  void scopeControlsRowVisibility() {
    setScope("");
    assertEquals(0, visibleMappers(), "no scope must see nothing (fail-closed)");

    setScope(TENANT_A);
    assertEquals(1, visibleMappers(), "tenant A sees only its own mapper");
    assertEquals(MAPPER_A, theOnlyVisibleMapper());

    setScope(TENANT_B);
    assertEquals(1, visibleMappers(), "tenant B sees only its own mapper");
    assertEquals(MAPPER_B, theOnlyVisibleMapper());

    setScope(TENANT_A + "," + TENANT_B);
    assertEquals(2, visibleMappers(), "a multi-tenant scope sees both");
  }

  @Test
  @DisplayName("a write under one scope cannot reach another tenant's mapper")
  void scopeProtectsWrites() {
    setScope(TENANT_A);
    assertEquals(0, deleteMapper(MAPPER_B), "tenant A must not delete tenant B's mapper");
    assertEquals(1, deleteMapper(MAPPER_A), "tenant A can delete its own mapper");

    setScope(TENANT_B);
    assertEquals(1, visibleMappers(), "tenant B's mapper is intact and still its own");
    assertEquals(MAPPER_B, theOnlyVisibleMapper());
  }

  private int deleteMapper(String name) {
    return entityManager
        .createNativeQuery("DELETE FROM import_mappers WHERE mapper_name = :name")
        .setParameter("name", name)
        .executeUpdate();
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private int visibleMappers() {
    Number count =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM import_mappers WHERE mapper_name IN (:a, :b)")
                .setParameter("a", MAPPER_A)
                .setParameter("b", MAPPER_B)
                .getSingleResult();
    return count.intValue();
  }

  private String theOnlyVisibleMapper() {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT mapper_name FROM import_mappers WHERE mapper_name IN (:a, :b)")
            .setParameter("a", MAPPER_A)
            .setParameter("b", MAPPER_B)
            .getSingleResult();
  }

  private void insertTenant(String id) {
    entityManager
        .createNativeQuery("INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :name)")
        .setParameter("id", id)
        .setParameter("name", id)
        .executeUpdate();
  }

  private void insertMapper(String name, String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO import_mappers"
                + " (mapper_id, mapper_name, mapper_inject_type_column, mapper_created_at,"
                + " mapper_updated_at, tenant_id)"
                + " VALUES (gen_random_uuid(), :name, 'inject_type', now(), now(), :tenant)")
        .setParameter("name", name)
        .setParameter("tenant", tenantId)
        .executeUpdate();
  }
}
