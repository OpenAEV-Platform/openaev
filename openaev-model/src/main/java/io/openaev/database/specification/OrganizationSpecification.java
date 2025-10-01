package io.openaev.database.specification;

import io.openaev.database.model.Organization;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class OrganizationSpecification {

  private OrganizationSpecification() {}

  public static Specification<Organization> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }

  public static Specification<Organization> findGrantedFor(@NotBlank final String userId) {
    return (root, query, cb) -> {
      query.distinct(true);
      Path<?> path = root.join("groups", JoinType.INNER).join("users", JoinType.INNER).get("id");
      return cb.equal(path, userId);
    };
  }
}
