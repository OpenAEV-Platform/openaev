package io.openaev.database.specification;

import io.openaev.database.model.CustomDashboard;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class CustomDashboardSpecification {

  private CustomDashboardSpecification() {}

  public static Specification<CustomDashboard> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
