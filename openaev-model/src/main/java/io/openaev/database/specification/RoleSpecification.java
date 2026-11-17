package io.openaev.database.specification;

import io.openaev.database.model.Role;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

public class RoleSpecification {

  public static Specification<Role> fromName(@NotBlank final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }

  // -- TENANT --

  public static Specification<Role> tenantScope(Collection<String> tenantIds) {
    return (root, query, cb) -> root.get("tenant").get("id").in(tenantIds);
  }

  // -- PLATFORM --

  public static Specification<Role> platformScope() {
    return (root, query, cb) -> cb.isNull(root.get("tenant"));
  }
}
