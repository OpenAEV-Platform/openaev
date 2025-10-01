package io.openaev.utils.pagination;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.data.domain.Sort;

public class SortUtilsCriteriaBuilder {

  private SortUtilsCriteriaBuilder() {}

  public static <T> List<Order> toSortCriteriaBuilder(CriteriaBuilder cb, Root<T> root, Sort sort) {
    List<Order> orders = new ArrayList<>();
    if (sort.isSorted()) {
      sort.forEach(
          order -> {
            if (StringUtils.hasText(order.getProperty())) {
              if (order.isAscending()) {
                orders.add(cb.asc(root.get(order.getProperty())));
              } else {
                orders.add(cb.desc(root.get(order.getProperty())));
              }
            }
          });
    } else {
      orders.add(cb.asc(root.get("id"))); // Default order by scenario_id
    }
    return orders;
  }
}
