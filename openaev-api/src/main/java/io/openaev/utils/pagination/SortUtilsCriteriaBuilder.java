package io.openaev.utils.pagination;

import jakarta.persistence.criteria.*;
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

  public record SortSpecification(List<Order> orders, List<Selection<?>> selections) {}

  public static <T> SortSpecification toSortCriteriaBuilderWithNullHandling(
      CriteriaBuilder cb, Root<T> root, Sort sort) {
    List<Order> orders = new ArrayList<>();
    List<Selection<?>> selections = new ArrayList<>();

    if (sort.isSorted()) {
      sort.forEach(
          order -> {
            if (!StringUtils.hasText(order.getProperty())) {
              return;
            }

            // Handle nulls if specified
            if (order.getNullHandling() == Sort.NullHandling.NULLS_FIRST
                || order.getNullHandling() == Sort.NullHandling.NULLS_LAST) {
              boolean isNullsFirst = order.getNullHandling() == Sort.NullHandling.NULLS_FIRST;
              Expression<Integer> nullOrderExpr =
                  cb.<Integer>selectCase()
                      .when(
                          cb.isNull(root.get(order.getProperty())),
                          (isNullsFirst ? cb.literal(0) : cb.literal(1)))
                      .otherwise(isNullsFirst ? cb.literal(1) : cb.literal(0));

              String alias = "null_order_" + order.getProperty();
              selections.add(nullOrderExpr.alias(alias));
              orders.add(cb.asc(nullOrderExpr));
            }

            // Add the actual sort
            if (order.isAscending()) {
              orders.add(cb.asc(root.get(order.getProperty())));
            } else {
              orders.add(cb.desc(root.get(order.getProperty())));
            }
          });
    } else {
      orders.add(cb.asc(root.get("id"))); // Default order by scenario_id
    }
    return new SortSpecification(orders, selections);
  }
}
