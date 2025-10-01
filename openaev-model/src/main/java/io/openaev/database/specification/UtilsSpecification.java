package io.openaev.database.specification;

import io.openaev.database.model.Base;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class UtilsSpecification {

  private UtilsSpecification() {}

  public static <T extends Base> Specification<T> byName(
      @Nullable final String searchText, @NotBlank final String property) {
    return (root, query, cb) -> {
      if (searchText == null || searchText.isEmpty()) {
        return cb.conjunction();
      }
      String likePattern = "%" + searchText.toLowerCase() + "%";
      return cb.like(cb.lower(root.get(property)), likePattern);
    };
  }
}
