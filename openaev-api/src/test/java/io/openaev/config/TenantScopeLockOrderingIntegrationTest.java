package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.aop.AccessControlAspect;
import io.openaev.aop.HibernateFilterTransactionAspect;
import io.openaev.aop.TenantScopeTransactionAspect;
import io.openaev.aop.audit_log.AccessControlAuditLogAspect;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockAspect;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.context.TxCtx;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves the precedence chain {@code @Lock -> transaction -> tenant scope -> audit -> RBAC} holds
 * for a method that carries all three. Two properties matter and both are checked against a real
 * Spring context and a real database, no mocks:
 *
 * <ul>
 *   <li>the tenant scope is set <b>inside</b> the active transaction even when {@code @Lock} is
 *       also on the method (the lock aspect does not push the scope out of the transaction);
 *   <li>the lock <b>wraps the whole transaction</b>: a second caller contending on the same key
 *       only enters after the first caller's transaction has committed. This is the property that
 *       breaks if the transaction interceptor is ever ordered outside the lock.
 * </ul>
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName(
    "Tenant scope composes with @Lock (lock wraps the transaction, scope is set inside it)")
class TenantScopeLockOrderingIntegrationTest {

  @Autowired private LockedProbe probe;
  @Autowired private BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor;

  @Test
  @DisplayName(
      "the transaction advisor really sits at LOWEST_PRECEDENCE - 4 (the order is effective)")
  void transactionAdvisorOrderIsApplied() {
    assertEquals(
        Ordered.LOWEST_PRECEDENCE - 4,
        transactionAdvisor.getOrder(),
        "@EnableTransactionManagement(order=...) must actually move the advisor; otherwise the lock"
            + " -> tx -> scope -> audit -> rbac ordering is illusory and still rests on an undefined"
            + " tie-break");
  }

  @Test
  @DisplayName("a @Lock + @Transactional method still sets the scope inside an active transaction")
  void lockedTransactionalMethodSetsScopeInsideTransaction() {
    String[] state = probe.lockedScopeWithinActiveTransaction(TxCtx.forTenant("t1"));
    assertEquals("true", state[0], "a real transaction must be active inside the locked method");
    assertEquals(
        "t1", state[1], "the scope must be set inside the transaction, with @Lock present");
  }

  @Test
  @DisplayName("the lock wraps the whole transaction: the writer commits before the reader enters")
  void lockWrapsTheWholeTransaction() throws Exception {
    List<String> events = new CopyOnWriteArrayList<>();
    CountDownLatch writerInside = new CountDownLatch(1);
    CountDownLatch releaseWriter = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> writer =
          pool.submit(
              () -> {
                probe.writeThenHold(writerInside, releaseWriter, events);
                return null;
              });
      assertTrue(
          writerInside.await(10, TimeUnit.SECONDS), "the writer must enter the locked transaction");

      Future<?> reader =
          pool.submit(
              () -> {
                probe.reader(events);
                return null;
              });
      // The reader contends on the same lock key; while the writer holds it, the reader cannot run.
      Thread.sleep(300);
      assertFalse(
          events.contains("reader-body"),
          "the reader must block on the lock while the writer holds it");

      releaseWriter.countDown();
      writer.get(10, TimeUnit.SECONDS);
      reader.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    assertEquals(
        List.of("writer-body", "writer-commit", "reader-body"),
        events,
        "the writer's transaction must commit before the reader enters: the lock wraps the tx");
  }

  @Test
  @DisplayName(
      "aspect ordering: Lock < Transaction < TenantFilter < TenantScope < Audit < RBAC (no ties)")
  void given_allAspects_should_respectStrictRelativeOrdering() {
    // Arrange
    int lockOrder = getOrderValue(LockAspect.class);
    int transactionOrder = transactionAdvisor.getOrder();
    int tenantFilterOrder = getOrderValue(HibernateFilterTransactionAspect.class);
    int tenantScopeOrder = getOrderValue(TenantScopeTransactionAspect.class);
    int auditOrder = getOrderValue(AccessControlAuditLogAspect.class);
    int rbacOrder = getOrderValue(AccessControlAspect.class);

    // Assert — strict ordering (lower value = outer aspect)
    assertThat(lockOrder)
        .as("LockAspect must wrap @Transactional (lower order)")
        .isLessThan(transactionOrder);
    assertThat(transactionOrder)
        .as("@Transactional must wrap the tenant filter aspect (lower order)")
        .isLessThan(tenantFilterOrder);
    assertThat(tenantFilterOrder)
        .as("the v1 tenant filter must be installed before the v2 tenant scope (lower order)")
        .isLessThan(tenantScopeOrder);
    assertThat(tenantScopeOrder)
        .as(
            "the tenant scope must wrap AuditLogAspect, and through it the RBAC aspect:"
                + " AccessControlAspect resolves a resource's parent by loading the entity, inside"
                + " this same transaction. Tied on LOWEST_PRECEDENCE that read could win and"
                + " hydrate v2-scoped associations as null (fail-closed can_access_tenant) before"
                + " the scope existed, and the controller would then serve the partial entity.")
        .isLessThan(auditOrder);
    assertThat(auditOrder)
        .as("AuditLogAspect must wrap AccessControlAspect (lower order)")
        .isLessThan(rbacOrder);
  }

  private static int getOrderValue(Class<?> clazz) {
    Order order = AnnotationUtils.findAnnotation(clazz, Order.class);
    assertThat(order).as("@Order must be present on %s", clazz.getSimpleName()).isNotNull();
    return order.value();
  }

  @TestConfiguration
  static class Config {
    @Bean
    LockedProbe lockedProbe(EntityManager entityManager) {
      return new LockedProbe(entityManager);
    }
  }

  /**
   * A bean whose methods carry {@code @Lock} + {@code @Transactional} so the full chain applies.
   */
  static class LockedProbe {
    private static final String KEY = "'tenant-lock-ordering'";
    private final EntityManager entityManager;

    LockedProbe(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Lock(type = LockResourceType.SECURITY_COVERAGE, key = KEY)
    @Transactional
    public String[] lockedScopeWithinActiveTransaction(TxCtx ctx) {
      boolean active = TransactionSynchronizationManager.isActualTransactionActive();
      String scope =
          (String)
              entityManager
                  .createNativeQuery(
                      "SELECT coalesce(current_setting('app.current_tenants', true), '')")
                  .getSingleResult();
      return new String[] {String.valueOf(active), scope};
    }

    @Lock(type = LockResourceType.SECURITY_COVERAGE, key = KEY)
    @Transactional
    public void writeThenHold(CountDownLatch inside, CountDownLatch release, List<String> events)
        throws InterruptedException {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              events.add("writer-commit");
            }
          });
      events.add("writer-body");
      inside.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("writer was never released");
      }
    }

    @Lock(type = LockResourceType.SECURITY_COVERAGE, key = KEY)
    @Transactional
    public void reader(List<String> events) {
      events.add("reader-body");
    }
  }
}
