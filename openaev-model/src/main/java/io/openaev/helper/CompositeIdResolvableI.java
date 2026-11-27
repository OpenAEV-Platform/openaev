package io.openaev.helper;

import com.fasterxml.jackson.databind.DeserializationContext;

public interface CompositeIdResolvableI {

  /**
   * Deserialization attribute naming the tenant a reference is resolved in, for a reader that knows
   * the tenant of what it reads (a chaining step reads its inject in the step's tenant). Without
   * it, a reference resolves in the ambient tenant.
   */
  String TENANT_ATTRIBUTE = CompositeIdResolvableI.class.getName() + ".tenant";

  Object resolveCompositeId(String rawId, DeserializationContext ctxt);

  /** The tenant {@link #TENANT_ATTRIBUTE} names for this read, or null when it names none. */
  static String namedTenant(DeserializationContext ctxt) {
    return ctxt != null && ctxt.getAttribute(TENANT_ATTRIBUTE) instanceof String tenantId
        ? tenantId
        : null;
  }
}
