package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

/**
 * Builds the tenant tables from the real Hibernate metamodel — the same call the wiring will make —
 * so a tenant-aware entity whose table cannot be resolved (e.g. a new entity without
 * {@code @Table}) fails here rather than at application startup.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("TenantTables built from the entity metamodel")
class TenantTablesModelTest {

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  @DisplayName("derives a valid set of tenant tables from every mapped entity")
  void buildsFromRealEntityModel() {
    List<Class<?>> entities =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .<Class<?>>map(EntityType::getJavaType)
            .toList();

    TenantTables tables = TenantTables.fromEntities(entities);

    assertFalse(tables.strict().isEmpty(), "expected strict tenant tables to be discovered");
    assertEquals(TenantTables.Family.STRICT, tables.family("documents"));
    assertEquals(TenantTables.Family.STRICT, tables.family("assets")); // SINGLE_TABLE inheritance
    assertEquals(TenantTables.Family.DUAL, tables.family("groups"));
    assertEquals(TenantTables.Family.DUAL, tables.family("parameters"));
  }
}
