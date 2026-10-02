package io.openaev.config;

import java.util.Set;

/**
 * One scope dimension the {@link ScopeStatementInspector} filters on — today the tenant, tomorrow
 * the marking clearance. A dimension answers two questions about a table: whether it is scoped at
 * all, and which SQL predicate restricts it to the current transaction's scope.
 *
 * <p>The predicate is returned as a string rather than a parsed expression so a dimension is free
 * to express itself as a plain function call ({@code can_access_tenant(t.tenant_id)}) or as a
 * correlated sub-query, without depending on the parser's expression types.
 *
 * <p>Read and write predicates are distinct because a read may be more permissive than a write: a
 * dual-scope tenant table lets platform rows through on a read but never on a write.
 */
public interface ScopeDimension {

  /** Short name used in the messages of refused statements, e.g. {@code tenant}. */
  String name();

  /**
   * Table names this dimension currently filters. Feeds the inspector's fast gate, so it must list
   * every table whose statements need rewriting; an empty set makes the dimension inert.
   */
  Set<String> activeTables();

  /** Whether this dimension scopes the given table. */
  boolean covers(String table);

  /**
   * Predicate restricting a read of {@code table} (referenced as {@code alias}) to the current
   * scope. Only called when {@link #covers(String)} is true.
   */
  String readPredicate(String table, String alias);

  /**
   * Predicate restricting a write to {@code table} (referenced as {@code alias}) to the current
   * scope. Only called when {@link #covers(String)} is true.
   */
  String writePredicate(String table, String alias);

  /**
   * Column whose written value must be validated on an {@code INSERT ... SELECT} into a covered
   * table, or {@code null} when the dimension does not guard writes by rewriting. Returning {@code
   * null} means write attribution is enforced elsewhere (a service-layer validator), not that it is
   * unguarded.
   */
  default String writeAttributionColumn() {
    return null;
  }

  /**
   * Predicate asserting that the value about to be written into {@link #writeAttributionColumn()}
   * is in scope. Only called when the column is non-null.
   */
  default String writeAttributionPredicate(String valueExpression) {
    return null;
  }
}
