package io.openaev.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.repository.ImportMapperRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TenantScopedTransaction#executeOnCurrentTransaction} runs work under another tenant scope
 * on the transaction already open, then puts the transaction's scope back. Changes pending before
 * the work are written under the scope they were made in, and so are the work's own.
 *
 * <p>Proven on the pilot active table ({@code import_mappers}), one row per tenant. Deliberately
 * NOT {@code @Transactional}: each test opens and commits its own transaction, and seeds and cleans
 * through auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@DisplayName("A tenant scope run on the current transaction")
class TenantScopedTransactionCurrentTransactionTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ImportMapperRepository importMapperRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManager manager;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String mapperA;
  private String mapperB;

  @BeforeEach
  void seedOneMapperPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("current-tx-a-");
    tenantB = seedTenant("current-tx-b-");
    mapperA = seedMapper(tenantA, "mapper-a");
    mapperB = seedMapper(tenantB, "mapper-b");
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM import_mappers WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + id);
    return id;
  }

  private String seedMapper(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?)",
        id,
        name,
        "inject_type",
        tenantId);
    return id;
  }

  private String storedName(String mapperId) {
    return jdbc.queryForObject(
        "SELECT mapper_name FROM import_mappers WHERE mapper_id = CAST(? AS uuid)",
        String.class,
        mapperId);
  }

  private String currentScope() {
    return (String)
        manager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  /** Runs {@code work} in a committed transaction scoped to tenant A. */
  private <T> T inTenantA(java.util.function.Supplier<T> work) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
              return work.get();
            });
  }

  private ImportMapper loadOnly() {
    return importMapperRepository.findAll().iterator().next();
  }

  @Nested
  @DisplayName("Scope")
  class Scope {

    @Test
    @DisplayName("given a transaction scoped to a tenant should run the work in the other one")
    void given_transactionScopedToATenant_should_runTheWorkInTheOtherOne() {
      // Act
      String seen =
          inTenantA(
              () ->
                  tenantTx
                      .executeOnCurrentTransaction(TxCtx.forTenant(tenantB), () -> loadOnly())
                      .getName());

      // Assert
      assertThat(seen).isEqualTo("mapper-b");
    }

    @Test
    @DisplayName("given work that returns should put the transaction's scope back")
    void given_workThatReturns_should_putTheTransactionsScopeBack() {
      // Act
      String after =
          inTenantA(
              () -> {
                tenantTx.executeOnCurrentTransaction(TxCtx.forTenant(tenantB), () -> null);
                return currentScope() + "|" + loadOnly().getName();
              });

      // Assert
      assertThat(after).isEqualTo(tenantA + "|mapper-a");
    }

    @Test
    @DisplayName("given work that throws should put the transaction's scope back")
    void given_workThatThrows_should_putTheTransactionsScopeBack() {
      // Act
      String after =
          inTenantA(
              () -> {
                try {
                  tenantTx.executeOnCurrentTransaction(
                      TxCtx.forTenant(tenantB),
                      () -> {
                        throw new java.io.IOException("work failed");
                      });
                } catch (java.io.IOException expected) {
                  // the checked exception of the work reaches the caller as is
                }
                return currentScope();
              });

      // Assert
      assertThat(after).isEqualTo(tenantA);
    }
  }

  @Nested
  @DisplayName("Pending changes")
  class PendingChanges {

    @Test
    @DisplayName(
        "given a change made before the work should write it under the scope it was made in")
    void given_changeMadeBeforeTheWork_should_writeItUnderItsOwnScope() {
      // Act
      inTenantA(
          () -> {
            loadOnly().setName("renamed-a");
            return tenantTx.executeOnCurrentTransaction(
                TxCtx.forTenant(tenantB), () -> importMapperRepository.count());
          });

      // Assert
      assertThat(storedName(mapperA)).isEqualTo("renamed-a");
    }

    @Test
    @DisplayName("given a change made by the work should write it under the work's scope")
    void given_changeMadeByTheWork_should_writeItUnderTheWorksScope() {
      // Act
      inTenantA(
          () ->
              tenantTx.executeOnCurrentTransaction(
                  TxCtx.forTenant(tenantB),
                  () -> {
                    loadOnly().setName("renamed-b");
                    return null;
                  }));

      // Assert
      assertThat(storedName(mapperB)).isEqualTo("renamed-b");
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("given no active transaction should refuse")
    void given_noActiveTransaction_should_refuse() {
      // Act and assert
      assertThatThrownBy(
              () -> tenantTx.executeOnCurrentTransaction(TxCtx.forTenant(tenantB), () -> null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("nothing to join");
    }

    @Test
    @DisplayName("given every tenant should refuse")
    void given_everyTenant_should_refuse() {
      // Act and assert
      assertThatThrownBy(
              () ->
                  inTenantA(
                      () -> tenantTx.executeOnCurrentTransaction(TxCtx.allTenants(), () -> null)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exactly one tenant");
    }

    @Test
    @DisplayName("given several tenants should refuse")
    void given_severalTenants_should_refuse() {
      // Act and assert
      assertThatThrownBy(
              () ->
                  inTenantA(
                      () ->
                          tenantTx.executeOnCurrentTransaction(
                              TxCtx.forTenants(java.util.List.of(tenantA, tenantB)), () -> null)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exactly one tenant");
    }

    @Test
    @DisplayName("given a missing scope should refuse")
    void given_missingScope_should_refuse() {
      // Act and assert
      assertThatThrownBy(
              () ->
                  inTenantA(
                      () -> tenantTx.executeOnCurrentTransaction(TxCtx.missing(), () -> null)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
