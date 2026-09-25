package io.openaev.context;

import java.util.Collection;
import java.util.List;

/**
 * Marking clearance carried by a database transaction: the set of marking ids the caller holds.
 *
 * <p>Deliberately shaped like {@link TxCtx} — three states, never {@code null}, serialized to a
 * comma-separated GUC — because both are scope dimensions read by the same statement inspector.
 * {@link #toGuc()} feeds {@code set_config('app.current_markings', …, true)}, read back by the
 * {@code is_marking_set_allowed(marking_ids)} SQL function.
 *
 * <p><b>The one place the analogy breaks, and it matters.</b> An empty tenant scope denies every
 * row ({@code can_access_tenant} is false for all of them), so {@link TxCtx.Missing} means "access
 * denied". An empty <i>marking</i> clearance denies only <i>marked</i> rows: the predicate is set
 * containment, and the empty set is contained in the empty set, so unmarked rows stay visible. That
 * is why the state here is called {@link None} rather than "missing" — holding no clearance is a
 * normal, safe state (it is what every user starts with), not an error. Fail-closed for marking
 * means "see less", not "see nothing".
 *
 * <p>Ordinality is already resolved by the time a value of this type exists: {@code TLP:AMBER}
 * implies {@code TLP:GREEN} and {@code TLP:CLEAR}, and that expansion happens in Java (see {@code
 * MarkingScopeResolver}). The set here is therefore <i>flat</i> — every id the caller holds, not
 * just the highest per type — which is what lets the SQL predicate be a plain containment test with
 * no notion of order.
 */
public sealed interface MarkingCtx permits MarkingCtx.None, MarkingCtx.Restricted, MarkingCtx.All {

  /** Value for {@code set_config('app.current_markings', …, true)}; never {@code null}. */
  String toGuc();

  /** No marking held: marked rows are hidden, unmarked rows remain visible. */
  static MarkingCtx none() {
    return None.INSTANCE;
  }

  /** Clearance restricted to an explicit, non-empty set of marking ids. */
  static MarkingCtx forMarkings(Collection<String> markingIds) {
    return markingIds.isEmpty() ? none() : new Restricted(List.copyOf(markingIds));
  }

  /**
   * The intention "this work must see every marked row" — system identity, the marking counterpart
   * of {@link TxCtx#allTenants()}. Not a wildcard: it is resolved into an explicit {@link
   * Restricted} list of the tenant's marking ids when the scope is set, and only {@code
   * TenantScopedTransaction} does that. Background jobs take this by default so that activating a
   * table stays a no-op for them.
   */
  static MarkingCtx all() {
    return All.INSTANCE;
  }

  record None() implements MarkingCtx {
    static final None INSTANCE = new None();

    /** Empty string: contains no marking, so only unmarked rows satisfy the predicate. */
    @Override
    public String toGuc() {
      return "";
    }
  }

  /** An unresolved intention: it cannot reach the scope channel, only its resolution can. */
  record All() implements MarkingCtx {
    static final All INSTANCE = new All();

    @Override
    public String toGuc() {
      throw new IllegalStateException(
          "all() is an unresolved intention: it cannot be serialized to the scope channel. Only"
              + " TenantScopedTransaction resolves it into the explicit marking list of the"
              + " tenant(s) in scope; it is not usable on the HTTP path.");
    }
  }

  record Restricted(List<String> markingIds) implements MarkingCtx {
    public Restricted {
      markingIds = List.copyOf(markingIds);
      if (markingIds.isEmpty()) {
        throw new IllegalArgumentException(
            "marking clearance must not be empty; use none() instead");
      }
      for (String id : markingIds) {
        if (id.isBlank()) {
          throw new IllegalArgumentException("marking id must not be blank");
        }
        if (id.indexOf(',') >= 0) {
          throw new IllegalArgumentException("marking id must not contain ','");
        }
      }
    }

    @Override
    public String toGuc() {
      return String.join(",", markingIds);
    }
  }
}
