package io.openaev.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.aop.TenantScopeTransactionAspect;
import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantScopeTransactionAspect — TxCtx detection in method arguments")
class TenantScopeTransactionAspectTest {

  private static final String SET_CONFIG_SQL =
      "SELECT set_config('app.current_tenants', :scope, true)";
  private static final String CURRENT_SETTING_SQL =
      "SELECT coalesce(current_setting('app.current_tenants', true), '')";

  private static JoinPoint joinPointWith(Object... args) {
    JoinPoint joinPoint = mock(JoinPoint.class);
    when(joinPoint.getArgs()).thenReturn(args);
    return joinPoint;
  }

  // Before setting a scope the aspect reads the current one (nesting guard); with none set yet it
  // reads back empty and proceeds. The guard's own behaviour is covered against a real database in
  // TenantScopeTransactionAspectIntegrationTest.
  private static void stubEmptyCurrentScope(EntityManager entityManager) {
    Query currentScope = mock(Query.class);
    when(entityManager.createNativeQuery(CURRENT_SETTING_SQL)).thenReturn(currentScope);
    when(currentScope.getSingleResult()).thenReturn("");
  }

  // --- no scope: the connection must never be touched ----------------------

  @Test
  @DisplayName("no arguments at all: the connection is never touched")
  void noArguments() {
    EntityManager entityManager = mock(EntityManager.class);
    new TenantScopeTransactionAspect(entityManager).applyScope(joinPointWith());
    verifyNoInteractions(entityManager);
  }

  @Test
  @DisplayName("a null argument array: the connection is never touched")
  void nullArguments() {
    EntityManager entityManager = mock(EntityManager.class);
    JoinPoint joinPoint = mock(JoinPoint.class);
    when(joinPoint.getArgs()).thenReturn(null);
    new TenantScopeTransactionAspect(entityManager).applyScope(joinPoint);
    verifyNoInteractions(entityManager);
  }

  @Test
  @DisplayName("arguments without a TxCtx: the connection is never touched")
  void argumentsWithoutTxCtx() {
    EntityManager entityManager = mock(EntityManager.class);
    new TenantScopeTransactionAspect(entityManager).applyScope(joinPointWith("some-id", 42));
    verifyNoInteractions(entityManager);
  }

  // --- a TxCtx: set_config is issued with the scope as a bound parameter ----

  @Test
  @DisplayName("a TxCtx argument: set_config is issued with the scope bound as a parameter")
  void txCtxArgumentIssuesSetConfig() {
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(query);
    when(query.setParameter("scope", "t1")).thenReturn(query);

    new TenantScopeTransactionAspect(entityManager)
        .applyScope(joinPointWith(TxCtx.forTenant("t1")));

    verify(entityManager).createNativeQuery(SET_CONFIG_SQL);
    verify(query).setParameter("scope", "t1");
    verify(query).getSingleResult();
  }

  @Test
  @DisplayName("a missing TxCtx: set_config is issued with an empty scope (fail-closed)")
  void missingTxCtxIssuesEmptyScope() {
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(query);
    when(query.setParameter("scope", "")).thenReturn(query);

    new TenantScopeTransactionAspect(entityManager).applyScope(joinPointWith(TxCtx.missing()));

    verify(query).setParameter("scope", "");
  }

  @Test
  @DisplayName("several TxCtx arguments: the first one wins")
  void firstTxCtxWins() {
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(query);
    when(query.setParameter("scope", "a")).thenReturn(query);

    new TenantScopeTransactionAspect(entityManager)
        .applyScope(joinPointWith(TxCtx.forTenant("a"), TxCtx.forTenant("b")));

    verify(query).setParameter("scope", "a");
  }
}
