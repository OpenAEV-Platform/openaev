package io.openaev.database.specification;

import io.openaev.database.model.Group;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class GroupSpecification {

  public static Specification<Group> defaultUserAssignable() {
    return (root, query, cb) -> cb.equal(root.get("defaultUserAssignation"), true);
  }

  public static Specification<Group> fromName(@NotBlank final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }
}
