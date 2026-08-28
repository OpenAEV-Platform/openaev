package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Readiness proof for the first table chosen for activation: {@code import_mappers}. It was picked
 * because it is strict tenant-scoped, has no native repository queries that could fail to parse,
 * has no JDBC bypass, and is reached only through HTTP flows (CRUD via the mapper API and
 * HTTP-triggered imports), never a background job. With the table activated, the transaction scope
 * alone decides which tenant's mappers a query sees and which a write can touch.
 */
@SpringBootTest(properties = "openaev.tenant.active-tables=import_mappers")
@DisplayName("First table activation readiness: import_mappers")
class ImportMapperTenantIsolationTest extends TenantIsolationIntegrationTest {

  private static final String TENANT_A = "mapper-tenant-a";
  private static final String TENANT_B = "mapper-tenant-b";
  private static final String MAPPER_A = "mapper-of-a";
  private static final String MAPPER_B = "mapper-of-b";

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() {
    seedTenant(TENANT_A);
    seedTenant(TENANT_B);
    insertMapper(MAPPER_A, TENANT_A);
    insertMapper(MAPPER_B, TENANT_B);
  }

  @Test
  @DisplayName("the active scope alone decides which tenant's mappers a query returns")
  void scopeControlsRowVisibility() {
    setScope("");
    assertEquals(0, countVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));

    setScope(TENANT_A);
    assertEquals(1, countVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));
    assertEquals(MAPPER_A, onlyVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));

    setScope(TENANT_B);
    assertEquals(1, countVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));
    assertEquals(MAPPER_B, onlyVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));

    setScope(TENANT_A + "," + TENANT_B);
    assertEquals(2, countVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));
  }

  @Test
  @DisplayName("a write under one scope cannot reach another tenant's mapper")
  void scopeProtectsWrites() {
    setScope(TENANT_A);
    assertEquals(
        0, deleteRow("import_mappers", "mapper_name", MAPPER_B), "A cannot delete B's mapper");
    assertEquals(1, deleteRow("import_mappers", "mapper_name", MAPPER_A), "A can delete its own");

    setScope(TENANT_B);
    assertEquals(1, countVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));
    assertEquals(MAPPER_B, onlyVisible("import_mappers", "mapper_name", MAPPER_A, MAPPER_B));
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
