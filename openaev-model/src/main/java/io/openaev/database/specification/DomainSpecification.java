package io.openaev.database.specification;

import io.openaev.database.model.Domain;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class DomainSpecification {

  public static Specification<Domain> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
