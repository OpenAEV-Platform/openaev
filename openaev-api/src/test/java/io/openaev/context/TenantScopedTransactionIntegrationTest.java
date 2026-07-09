package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.ImportMapperRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the background transaction primitive end to end on the active pilot table ({@code
 * import_mappers}): the scope is set at open and gone after; a scope-less transaction is
 * fail-closed; the primitive refuses to open inside an active transaction (a scope boundary is
 * explicit); a nested {@code executeNew} re-sets the scope or sees nothing; a joined
 * {@code @Transactional} service keeps the primitive's scope and the aspect stays inert without a
 * {@code TxCtx} argument.
 *
 * <p>Deliberately NOT {@code @Transactional}: the primitive opens its own transactions, and {@code
 * execute} must be provable against a test-managed transaction. Seeding and cleanup run in
 * auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@Import(TenantScopedTransactionIntegrationTest.JoinedServiceFixture.class)
@DisplayName("TenantScopedTransaction: the background transaction primitive")
class TenantScopedTransactionIntegrationTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ImportMapperRepository importMapperRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;
  @Autowired private JoinedService joinedService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("primitive-a-" + UUID.randomUUID());
    tenantB = seedTenant("primitive-b-" + UUID.randomUUID());
    seedMapper(tenantA, "mapper-a");
    seedMapper(tenantB, "mapper-b");
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM import_mappers WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Test
  @DisplayName("the scope is visible inside the transaction and gone after it")
  void scopeIsTransactionLocal() {
    String inside =
        tenantTx.execute(TxCtx.forTenant(tenantA), this::currentScopeInCurrentTransaction);
    assertEquals(tenantA, inside, "inside the primitive the GUC carries the scope");

    String after =
        rawTransaction(TransactionDefinition.PROPAGATION_REQUIRED)
            .execute(status -> currentScopeInCurrentTransaction());
    assertEquals("", after, "set_config is transaction-local: nothing survives the commit");
  }

  @Test
  @DisplayName("fail-closed: a raw TransactionTemplate reads 0 rows, the primitive reads them")
  void rawTransactionIsFailClosedPrimitiveIsNot() {
    Long unscoped =
        rawTransaction(TransactionDefinition.PROPAGATION_REQUIRED)
            .execute(status -> importMapperRepository.count());
    assertEquals(0L, unscoped, "no scope set: the active table must go dark");

    long scoped = tenantTx.execute(TxCtx.forTenant(tenantA), importMapperRepository::count);
    assertEquals(1L, scoped, "the primitive set the scope: tenant A sees exactly its row");

    assertEquals(
        2L,
        jdbc.queryForObject(
            "SELECT count(*) FROM import_mappers WHERE tenant_id IN (?, ?)",
            Long.class,
            tenantA,
            tenantB),
        "ground truth: both rows exist, the difference is only the scope");
  }

  @Test
  @DisplayName("execute() refuses to open inside an already active transaction")
  void executeRefusesActiveTransaction() {
    IllegalStateException refusal =
        assertThrows(
            IllegalStateException.class,
            () ->
                rawTransaction(TransactionDefinition.PROPAGATION_REQUIRED)
                    .execute(
                        status ->
                            tenantTx.execute(TxCtx.forTenant(tenantA), () -> "should not run")));
    assertTrue(
        refusal.getMessage().contains("active transaction"),
        "the refusal must name the reason: " + refusal.getMessage());

    // forEachTenant is a sequence of top-level transactions: inside an active one it must refuse
    // upfront with the same named reason, not fail every tenant and throw an aggregate.
    IllegalStateException loopRefusal =
        assertThrows(
            IllegalStateException.class,
            () ->
                rawTransaction(TransactionDefinition.PROPAGATION_REQUIRED)
                    .execute(
                        status -> {
                          tenantTx.forEachTenant(ctx -> {});
                          return null;
                        }));
    assertTrue(
        loopRefusal.getMessage().contains("active transaction"),
        "forEachTenant must refuse upfront, not per tenant: " + loopRefusal.getMessage());
  }

  @Test
  @DisplayName("nested: a new inner transaction sees nothing unless executeNew re-sets the scope")
  void nestedTransactionNeedsItsOwnScope() {
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () -> {
          Long innerWithoutScope =
              rawTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
                  .execute(status -> importMapperRepository.count());
          assertEquals(0L, innerWithoutScope, "a new inner transaction does not inherit the scope");

          long innerRescoped =
              tenantTx.executeNew(TxCtx.forTenant(tenantA), importMapperRepository::count);
          assertEquals(1L, innerRescoped, "executeNew re-sets the scope in the new transaction");

          assertEquals(
              1L,
              (long) importMapperRepository.count(),
              "back in the outer transaction, the original scope still holds");
          return null;
        });
  }

  @Test
  @DisplayName("the scope does not cross threads: another thread's transaction stays fail-closed")
  void scopeDoesNotLeakAcrossThreads() {
    // The scope lives in a transaction-local GUC on THIS transaction's connection. A concurrent
    // thread (executor, detached hand-off) gets its own connection and transaction: no scope, so
    // the active table goes dark there — fail-closed, never inherited. Pins what was previously
    // argued by construction only.
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () -> {
          assertEquals(1L, (long) importMapperRepository.count(), "scoped in the opening thread");
          Long fromOtherThread =
              CompletableFuture.supplyAsync(
                      () ->
                          rawTransaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
                              .execute(status -> importMapperRepository.count()))
                  .join();
          assertEquals(
              0L, fromOtherThread, "another thread carries no scope: the table must go dark");
          return null;
        });
  }

  @Test
  @DisplayName("a joined @Transactional service keeps the scope; the aspect is inert without TxCtx")
  void joinedTransactionalServiceKeepsTheScope() {
    long viaJoinedService = tenantTx.execute(TxCtx.forTenant(tenantA), joinedService::countMappers);
    assertEquals(
        1L,
        viaJoinedService,
        "REQUIRED joins the primitive's transaction, so the scope set at open still applies");
  }

  @Test
  @DisplayName("execute() refuses a null or Missing scope: no background transaction without one")
  void executeRefusesNullAndMissingScope() {
    assertThrows(NullPointerException.class, () -> tenantTx.execute(null, () -> "no"));
    IllegalArgumentException refusal =
        assertThrows(
            IllegalArgumentException.class, () -> tenantTx.execute(TxCtx.missing(), () -> "no"));
    assertTrue(
        refusal.getMessage().contains("Missing"),
        "the refusal must name the reason: " + refusal.getMessage());
  }

  @Test
  @DisplayName("executeNew() refuses to run outside an active transaction: execute() is the door")
  void executeNewRefusesOutsideActiveTransaction() {
    IllegalStateException refusal =
        assertThrows(
            IllegalStateException.class,
            () -> tenantTx.executeNew(TxCtx.forTenant(tenantA), () -> "should not run"));
    assertTrue(
        refusal.getMessage().contains("execute()"),
        "the refusal must point at the right door: " + refusal.getMessage());
  }

  @Test
  @DisplayName("an exception rolls back the writes and the next transaction carries no scope")
  void exceptionRollsBackWritesAndLeavesNoScope() {
    String doomedId = UUID.randomUUID().toString();
    assertThrows(
        IllegalStateException.class,
        () ->
            tenantTx.execute(
                TxCtx.forTenant(tenantA),
                () -> {
                  entityManager
                      .createNativeQuery(
                          "INSERT INTO import_mappers (mapper_id, mapper_name,"
                              + " mapper_inject_type_column, tenant_id)"
                              + " VALUES (CAST(:id AS uuid), :name, :col, :tenant)")
                      .setParameter("id", doomedId)
                      .setParameter("name", "doomed")
                      .setParameter("col", "inject_type")
                      .setParameter("tenant", tenantA)
                      .executeUpdate();
                  throw new IllegalStateException("boom");
                }));

    assertEquals(
        0L,
        jdbc.queryForObject(
            "SELECT count(*) FROM import_mappers WHERE mapper_id = CAST(? AS uuid)",
            Long.class,
            doomedId),
        "the write must be rolled back with the transaction");
    String after =
        rawTransaction(TransactionDefinition.PROPAGATION_REQUIRED)
            .execute(status -> currentScopeInCurrentTransaction());
    assertEquals("", after, "a following transaction must carry no scope");
  }

  @Test
  @DisplayName("the Runnable overloads run their work under the same guards")
  void runnableOverloadsRunTheWork() {
    java.util.concurrent.atomic.AtomicBoolean outerRan =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicBoolean innerRan =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () -> {
          outerRan.set(true);
          tenantTx.executeNew(TxCtx.forTenant(tenantA), () -> innerRan.set(true));
        });
    assertTrue(outerRan.get(), "the Runnable execute overload must run its work");
    assertTrue(innerRan.get(), "the Runnable executeNew overload must run its work");
  }

  private String currentScopeInCurrentTransaction() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  private TransactionTemplate rawTransaction(int propagation) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(propagation);
    return template;
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
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

  /**
   * A service whose method is @Transactional WITHOUT a TxCtx parameter: it must join and inherit.
   */
  public static class JoinedService {
    private final ImportMapperRepository repository;

    public JoinedService(ImportMapperRepository repository) {
      this.repository = repository;
    }

    @Transactional
    public long countMappers() {
      return repository.count();
    }
  }

  @TestConfiguration
  static class JoinedServiceFixture {
    @Bean
    JoinedService joinedService(ImportMapperRepository repository) {
      return new JoinedService(repository);
    }
  }
}
