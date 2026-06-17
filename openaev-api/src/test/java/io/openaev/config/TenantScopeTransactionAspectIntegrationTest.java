package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.context.TxCtx;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Verifies that the {@code TxCtx} passed to a {@code @Transactional} method is written into the
 * transaction-local {@code app.current_tenants} setting, across the propagations the application
 * uses, and that the scope never leaks beyond its own transaction.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Tenant scope at transaction open (set_config from the TxCtx parameter)")
class TenantScopeTransactionAspectIntegrationTest {

  @Autowired private ScopeProbe probe;

  // --- the scope written from the TxCtx parameter --------------------------

  @Test
  @DisplayName("the TxCtx parameter becomes the transaction's app.current_tenants")
  void txCtxParameterBecomesTheTransactionSetting() {
    assertEquals("t1", probe.currentScope(TxCtx.forTenant("t1")), "single tenant");
    assertEquals(
        "t1,t2", probe.currentScope(TxCtx.forTenants(List.of("t1", "t2"))), "multiple tenants");
    assertEquals("", probe.currentScope(TxCtx.missing()), "missing scope = empty (fail-closed)");
  }

  @Test
  @DisplayName("a method without a TxCtx parameter leaves the setting untouched")
  void withoutTxCtxParameterTheSettingIsUntouched() {
    assertEquals("", probe.currentScopeWithoutContext());
  }

  // --- ordering guard (T6.1): the aspect must run inside the transaction ---

  @Test
  @DisplayName(
      "ordering guard (T6.1): the scope is set inside an active transaction, not before BEGIN")
  void scopeIsAppliedInsideTheTransaction() {
    String[] state = probe.scopeWithinActiveTransaction(TxCtx.forTenant("t1"));
    assertEquals("true", state[0], "a real transaction must be active when the scope is read");
    // An empty scope here means the aspect ran before BEGIN: set_config(..., true) landed in a
    // throwaway auto-commit and never reached this transaction. That is the ordering regression
    // T6.1 must prevent, whatever mechanism is chosen (lock -> transaction -> scope).
    assertEquals("t1", state[1], "the scope must be visible inside the transaction");
  }

  // --- load-bearing assumption: applied inside the tx, for every propagation ---

  @Test
  @DisplayName("the scope is applied in a REQUIRES_NEW transaction")
  void scopeAppliedInRequiresNewTransaction() {
    assertEquals("t1", probe.currentScopeRequiresNew(TxCtx.forTenant("t1")));
  }

  @Test
  @DisplayName("the scope is applied in a read-only transaction")
  void scopeAppliedInReadOnlyTransaction() {
    assertEquals("t1", probe.currentScopeReadOnly(TxCtx.forTenant("t1")));
  }

  // --- no leak across transactions (the v1 trap this design removes) -------

  @Test
  @DisplayName("the scope does not leak into the next transaction on a reused connection")
  void scopeDoesNotLeakToNextTransaction() {
    assertEquals("t1", probe.currentScope(TxCtx.forTenant("t1")));
    assertEquals(
        "", probe.currentScopeWithoutContext(), "the next unscoped transaction sees no scope");
    assertEquals(
        "t2", probe.currentScope(TxCtx.forTenant("t2")), "a later scoped transaction is not stale");
  }

  @Test
  @DisplayName("a rolled-back transaction does not leak its scope to the next one")
  void rolledBackScopeDoesNotLeak() {
    assertThrows(RuntimeException.class, () -> probe.setScopeThenThrow(TxCtx.forTenant("t1")));
    assertEquals("", probe.currentScopeWithoutContext());
  }

  // --- nesting -------------------------------------------------------------

  @Test
  @DisplayName("a nested REQUIRED call without a TxCtx keeps the outer scope (same transaction)")
  void nestedRequiredInheritsOuterScope() {
    assertEquals("t1", probe.outerScopeThenInnerRequired(TxCtx.forTenant("t1")));
  }

  @Test
  @DisplayName("a nested REQUIRES_NEW call without a TxCtx is fail-closed and isolated")
  void nestedRequiresNewIsFailClosedAndIsolated() {
    String[] result = probe.outerScopeThenInnerRequiresNew(TxCtx.forTenant("t1"));
    assertEquals("", result[0], "the new inner transaction sees no scope (fail-closed)");
    assertEquals("t1", result[1], "the outer transaction's scope is intact afterwards");
  }

  // --- beans ---------------------------------------------------------------

  @TestConfiguration
  static class ProbeConfig {
    @Bean
    InnerTxBean innerTxBean(EntityManager entityManager) {
      return new InnerTxBean(entityManager);
    }

    @Bean
    ScopeProbe scopeProbe(EntityManager entityManager, InnerTxBean innerTxBean) {
      return new ScopeProbe(entityManager, innerTxBean);
    }
  }

  static String readScope(EntityManager entityManager) {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  /** Reports the scope visible inside its own transaction, under the test's propagations. */
  static class ScopeProbe {
    private final EntityManager entityManager;
    private final InnerTxBean innerTxBean;

    ScopeProbe(EntityManager entityManager, InnerTxBean innerTxBean) {
      this.entityManager = entityManager;
      this.innerTxBean = innerTxBean;
    }

    @Transactional
    public String currentScope(TxCtx ctx) {
      return readScope(entityManager);
    }

    /** Reports both whether a real transaction is active and the scope visible inside it. */
    @Transactional
    public String[] scopeWithinActiveTransaction(TxCtx ctx) {
      boolean active = TransactionSynchronizationManager.isActualTransactionActive();
      return new String[] {String.valueOf(active), readScope(entityManager)};
    }

    @Transactional
    public String currentScopeWithoutContext() {
      return readScope(entityManager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String currentScopeRequiresNew(TxCtx ctx) {
      return readScope(entityManager);
    }

    @Transactional(readOnly = true)
    public String currentScopeReadOnly(TxCtx ctx) {
      return readScope(entityManager);
    }

    @Transactional
    public void setScopeThenThrow(TxCtx ctx) {
      // the aspect has already set the scope before this body runs; the rollback must discard it
      throw new RuntimeException("boom");
    }

    @Transactional
    public String outerScopeThenInnerRequired(TxCtx ctx) {
      return innerTxBean.readScopeRequired();
    }

    @Transactional
    public String[] outerScopeThenInnerRequiresNew(TxCtx ctx) {
      String inner = innerTxBean.readScopeRequiresNew();
      String outerAfter = readScope(entityManager);
      return new String[] {inner, outerAfter};
    }
  }

  /**
   * A separate bean so nested {@code @Transactional} calls go through the proxy, not
   * self-invocation.
   */
  static class InnerTxBean {
    private final EntityManager entityManager;

    InnerTxBean(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String readScopeRequired() {
      return readScope(entityManager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String readScopeRequiresNew() {
      return readScope(entityManager);
    }
  }
}
