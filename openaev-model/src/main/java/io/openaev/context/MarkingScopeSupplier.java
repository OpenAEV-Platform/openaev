package io.openaev.context;

/**
 * Supplies the marking clearance of the current caller, for the tenant scope of the transaction
 * being opened.
 *
 * <p><b>Why this interface exists.</b> The scope aspect lives in {@code openaev-model}, but a
 * caller's clearance is derived from the authenticated principal and a cache that both live in
 * {@code openaev-api}, and {@code openaev-model} does not depend on {@code openaev-api}. Rather
 * than split scope-setting across two aspects — which would make their relative order load-bearing
 * and duplicate the "a transaction's scope is set once" rule — the model declares what it needs and
 * the API supplies it.
 *
 * <p>The clearance is <b>derived, never passed</b>. Marking scope is not a REST parameter: adding
 * it to endpoint signatures would put a security boundary in the hands of every controller author,
 * and a forgotten parameter would be a silent widening. The tenant dimension can afford to be an
 * explicit argument because the caller legitimately chooses <i>which</i> of their tenants to act
 * in; nobody chooses their own clearance.
 *
 * <p>Implementations must be <b>fail-closed</b>: with no principal, or no resolvable clearance,
 * return {@link MarkingCtx#none()}. Note what that does and does not mean here — an empty clearance
 * still sees unmarked rows, so it degrades to "see less", not "see nothing". Never return {@link
 * MarkingCtx#all()}: it is an unresolved intention reserved for the background primitive and throws
 * on serialization.
 */
@FunctionalInterface
public interface MarkingScopeSupplier {

  /**
   * The clearance to write into {@code app.current_markings} for this transaction.
   *
   * @param tenantScope the tenant scope already resolved for the transaction. Clearance is
   *     per-tenant — a marking definition belongs to exactly one tenant — so the answer depends on
   *     it. This is the one place the two scope dimensions meet, and it is a dependency of the
   *     clearance <i>computation</i>, not of the SQL rewrite.
   */
  MarkingCtx clearanceFor(TxCtx tenantScope);
}
