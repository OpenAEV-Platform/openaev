package io.openaev.helper;

import com.fasterxml.jackson.databind.DeserializationContext;

public interface CompositeIdResolvableI {

  /**
   * Deserialization attribute naming the tenant a reference is resolved in. A reader that resolves
   * references to rows names the tenant of what it reads (a chaining step reads its inject in the
   * step's tenant); a read that names none is refused.
   */
  String TENANT_ATTRIBUTE = CompositeIdResolvableI.class.getName() + ".tenant";

  Object resolveCompositeId(String rawId, DeserializationContext ctxt);

  /**
   * The tenant {@link #TENANT_ATTRIBUTE} names for this read.
   *
   * @throws IllegalStateException when the read names no tenant
   */
  static String namedTenant(DeserializationContext ctxt, Class<?> entityType) {
    if (ctxt != null && ctxt.getAttribute(TENANT_ATTRIBUTE) instanceof String tenantId) {
      return tenantId;
    }
    throw new IllegalStateException(
        "No tenant named to resolve a reference to " + entityType.getSimpleName());
  }
}
