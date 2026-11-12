package io.openaev.database.specification;

import io.openaev.database.model.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {

  private UserSpecification() {}

  /** Filters users by a single ID. */
  public static Specification<User> byId(@NotBlank final String id) {
    return (root, query, cb) -> cb.equal(root.get("id"), id);
  }

  /** Filters users whose ID is in the given list. */
  public static Specification<User> fromIds(@NotNull final List<String> ids) {
    return (root, query, cb) -> root.get("id").in(ids);
  }

  /**
   * Restricts to the given tenant every join of the user on {@code organizations} that has no
   * condition yet, such as the one a generic {@code user_organization} filter creates. A user holds
   * one organization per tenant, so without it a filter would also match the organizations the user
   * holds in the other tenants of the caller.
   *
   * <p>It adds no predicate: it must be composed <em>after</em> the specification that creates the
   * join, in the result query and in the count query.
   */
  public static Specification<User> organizationsInTenant(@NotBlank final String tenantId) {
    return (root, query, cb) -> {
      root.getJoins().stream()
          .filter(join -> "organizations".equals(join.getAttribute().getName()))
          .filter(join -> join.getOn() == null)
          .forEach(join -> join.on(cb.equal(join.get("tenant").get("id"), tenantId)));
      return null;
    };
  }

  /** Filters users that belong to the given tenant via the {@code users_tenants} join table. */
  public static Specification<User> inTenant(@NotBlank final String tenantId) {
    return (root, query, cb) -> cb.equal(root.join("tenants").get("id"), tenantId);
  }
}
