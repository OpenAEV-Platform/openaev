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

  /** Filters users that belong to the given tenant via the {@code users_tenants} join table. */
  public static Specification<User> inTenant(@NotBlank final String tenantId) {
    return (root, query, cb) -> cb.equal(root.join("tenants").get("id"), tenantId);
  }
}
