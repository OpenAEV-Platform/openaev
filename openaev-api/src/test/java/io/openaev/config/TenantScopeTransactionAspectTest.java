package io.openaev.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.aop.TenantScopeTransactionAspect;
import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("TenantScopeTransactionAspect — TxCtx detection in method arguments")
class TenantScopeTransactionAspectTest {

  private static final String SET_CONFIG_SQL =
      "SELECT set_config('app.current_tenants', :scope, true)";
  private static final String CURRENT_SETTING_SQL =
      "SELECT coalesce(current_setting('app.current_tenants', true), '')";
  private static final String SET_MARKING_SQL =
      "SELECT set_config('app.current_markings', :scope, true)";

  /**
   * The aspect with no marking supplier wired — the model-only arrangement. Marking scope is then
   * written empty, which still admits unmarked rows.
   */
  @SuppressWarnings("unchecked")
  private static TenantScopeTransactionAspect aspect(EntityManager entityManager) {
    return new TenantScopeTransactionAspect(entityManager, mock(ObjectProvider.class));
  }

  /** The aspect with a supplier that returns the given clearance. */
  @SuppressWarnings("unchecked")
  private static TenantScopeTransactionAspect aspect(
      EntityManager entityManager, MarkingCtx clearance) {
    ObjectProvider<MarkingScopeSupplier> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(ctx -> clearance);
    return new TenantScopeTransactionAspect(entityManager, provider);
  }

  /** The marking write always follows the tenant write, so every scoped test needs it stubbed. */
  private static Query stubMarkingWrite(EntityManager entityManager) {
    Query marking = mock(Query.class);
    when(entityManager.createNativeQuery(SET_MARKING_SQL)).thenReturn(marking);
    when(marking.setParameter(eq("scope"), any())).thenReturn(marking);
    return marking;
  }

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
    aspect(entityManager).applyScope(joinPointWith());
    verifyNoInteractions(entityManager);
  }

  @Test
  @DisplayName("a null argument array: the connection is never touched")
  void nullArguments() {
    EntityManager entityManager = mock(EntityManager.class);
    JoinPoint joinPoint = mock(JoinPoint.class);
    when(joinPoint.getArgs()).thenReturn(null);
    aspect(entityManager).applyScope(joinPoint);
    verifyNoInteractions(entityManager);
  }

  @Test
  @DisplayName("arguments without a TxCtx: the connection is never touched")
  void argumentsWithoutTxCtx() {
    EntityManager entityManager = mock(EntityManager.class);
    aspect(entityManager).applyScope(joinPointWith("some-id", 42));
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
    stubMarkingWrite(entityManager);

    aspect(entityManager).applyScope(joinPointWith(TxCtx.forTenant("t1")));

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
    stubMarkingWrite(entityManager);

    aspect(entityManager).applyScope(joinPointWith(TxCtx.missing()));

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
    stubMarkingWrite(entityManager);

    aspect(entityManager).applyScope(joinPointWith(TxCtx.forTenant("a"), TxCtx.forTenant("b")));

    verify(query).setParameter("scope", "a");
  }

  // --- the marking dimension: derived, never passed -------------------------

  @Test
  @DisplayName("a TxCtx argument: the marking scope is written alongside the tenant scope")
  void txCtxArgumentAlsoWritesMarkingScope() {
    // -- ARRANGE --
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query tenant = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(tenant);
    when(tenant.setParameter("scope", "t1")).thenReturn(tenant);
    Query marking = stubMarkingWrite(entityManager);

    // -- ACT --
    // The clearance is NOT an argument: the supplier derives it. That is the whole point — a
    // controller author cannot forget it, and cannot widen it.
    aspect(entityManager, MarkingCtx.forMarkings(List.of("m-green", "m-amber")))
        .applyScope(joinPointWith(TxCtx.forTenant("t1")));

    // -- ASSERT --
    verify(marking).setParameter("scope", "m-green,m-amber");
    verify(marking).getSingleResult();
  }

  @Test
  @DisplayName("no marking supplier wired: the marking scope is written empty, not skipped")
  void absentSupplierWritesEmptyMarkingScope() {
    // -- ARRANGE --
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query tenant = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(tenant);
    when(tenant.setParameter("scope", "t1")).thenReturn(tenant);
    Query marking = stubMarkingWrite(entityManager);

    // -- ACT --
    aspect(entityManager).applyScope(joinPointWith(TxCtx.forTenant("t1")));

    // -- ASSERT --
    // Written, not skipped: an unwritten setting could be inherited from an earlier transaction on
    // a reused connection. Empty is fail-closed for marking — unmarked rows still come through.
    verify(marking).setParameter("scope", "");
  }

  @Test
  @DisplayName("a caller holding no clearance: empty marking scope, which still sees unmarked rows")
  void noClearanceWritesEmptyMarkingScope() {
    // -- ARRANGE --
    EntityManager entityManager = mock(EntityManager.class);
    stubEmptyCurrentScope(entityManager);
    Query tenant = mock(Query.class);
    when(entityManager.createNativeQuery(SET_CONFIG_SQL)).thenReturn(tenant);
    when(tenant.setParameter("scope", "t1")).thenReturn(tenant);
    Query marking = stubMarkingWrite(entityManager);

    // -- ACT --
    aspect(entityManager, MarkingCtx.none()).applyScope(joinPointWith(TxCtx.forTenant("t1")));

    // -- ASSERT --
    verify(marking).setParameter("scope", "");
  }

  @Test
  @DisplayName("no TxCtx argument: neither setting is written, the aspect stays inert")
  void withoutTxCtxNeitherSettingIsWritten() {
    // -- ARRANGE --
    EntityManager entityManager = mock(EntityManager.class);

    // -- ACT --
    aspect(entityManager, MarkingCtx.forMarkings(List.of("m-green")))
        .applyScope(joinPointWith("some-id"));

    // -- ASSERT --
    // A method that opted out of tenant scope is not silently opted in to marking scope either.
    verifyNoInteractions(entityManager);
  }
}
