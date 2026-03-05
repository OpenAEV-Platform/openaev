package io.openaev.database.specification;

import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {

  private UserSpecification() {}

  /**
   * Filters users that belong to a specific tenant via the users_tenants join table.
   */
  public static Specification<User> inTenant(String tenantId) {
    return (root, query, cb) -> {
      Join<User, Tenant> tenantJoin = root.join("tenants");
      return cb.equal(tenantJoin.get("id"), tenantId);
    };
  }

  public static Specification<User> accessibleFromOrganizations(List<String> organizationIds) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.or(
            criteriaBuilder.isNull(root.get("organization")),
            root.get("organization").get("id").in(organizationIds));
  }

  public static Specification<User> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }
}
