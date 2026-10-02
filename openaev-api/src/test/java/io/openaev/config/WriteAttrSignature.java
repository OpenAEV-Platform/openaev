package io.openaev.config;

import io.openaev.database.model.Tenant;

/**
 * The signature grammar shared by the write-attribution recorder, the gate and the baseline file.
 *
 * <p>A signature is what a waiver is keyed on. It is intentionally coarser than a single call site:
 * {@code table} + the written tenant's relation to the transaction scope + the outermost production
 * entry frame (the controller method, or the background entry point). The entry frame is stable
 * whether the insert flushes at the write site or at a later autoflush in the same request, which
 * the innermost caller is not: a lazily flushed JPA insert is charged to whatever ran the flush.
 * The innermost caller is kept in the recorded violation as information, never in the signature.
 */
final class WriteAttrSignature {

  private WriteAttrSignature() {}

  /** The relation of a written {@code tenant_id} to the transaction's v2 scope. */
  enum Relation {
    /** The default tenant, while the scope is some other tenant. */
    DEFAULT,
    /** A non-default tenant outside the scope. */
    OTHER,
    /** No tenant at all ({@code tenant_id IS NULL}). */
    NULL
  }

  /** The marker the trigger raises for a null-tenant write, distinct from any real tenant id. */
  static final String NULL_TENANT_MARKER = "NULL";

  /**
   * Classifies a written tenant against the default tenant. A write inside the scope never reaches
   * here: the trigger only raises for a tenant outside the scope, or for a null tenant.
   */
  static Relation relationOf(String writtenTenant) {
    if (writtenTenant == null || NULL_TENANT_MARKER.equals(writtenTenant)) {
      return Relation.NULL;
    }
    return Tenant.DEFAULT_TENANT_UUID.equals(writtenTenant) ? Relation.DEFAULT : Relation.OTHER;
  }

  /**
   * The waiver key: {@code table relation entry-frame}, the entry frame reduced to a stable form so
   * the frozen baseline does not churn: without its line number (a line shift must not matter) and
   * without any dynamic-proxy decoration (a Spring CGLIB proxy carries a per-class counter that is
   * not guaranteed across runs). {@code entryFrame} is never null here; a write with no production
   * entry frame is test-driven and the gate waives it before it asks for a signature.
   */
  static String of(String table, Relation relation, String entryFrame) {
    return table + " " + relation.name() + " " + stripProxy(stripLine(entryFrame));
  }

  /** {@code io.openaev.Foo.bar:42} -> {@code io.openaev.Foo.bar}. */
  static String stripLine(String frame) {
    int colon = frame.lastIndexOf(':');
    return colon < 0 ? frame : frame.substring(0, colon);
  }

  /**
   * Removes dynamic-proxy decoration so the same production method has one key however Spring
   * wrapped it: {@code TenantService$$SpringCGLIB$$0.create} -> {@code TenantService.create}.
   * Everything from a {@code $$} run up to the next {@code .} is generated (CGLIB proxies,
   * lambdas); a single {@code $} is a genuine nested class and is kept.
   */
  static String stripProxy(String frame) {
    return frame.replaceAll("\\$\\$[^.]*", "");
  }
}
