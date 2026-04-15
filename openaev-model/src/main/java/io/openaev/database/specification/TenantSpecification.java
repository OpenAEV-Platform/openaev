package io.openaev.database.specification;

import io.openaev.database.model.Base;
import io.openaev.database.model.TenantBase;
import org.springframework.data.jpa.domain.Specification;

public class TenantSpecification {

  /**
   * Filters entities by tenant ID. Only applies to entities implementing {@link TenantBase}; for
   * other entities, returns an always-true predicate.
   *
   * @param tenantId the tenant ID to filter on
   */
  public static <T extends Base> Specification<T> fromTenant(final String tenantId) {
    return (root, query, cb) -> {
      if (TenantBase.class.isAssignableFrom(root.getJavaType())) {
        return cb.equal(root.get("tenant").get("id"), tenantId);
      }
      return cb.conjunction();
    };
  }
}
