package io.openaev.database.specification;

import io.openaev.database.model.User;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {

  private UserSpecification() {}

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
