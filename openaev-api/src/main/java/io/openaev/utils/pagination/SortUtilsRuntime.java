package io.openaev.utils.pagination;

import java.util.List;
import javax.annotation.Nullable;
import org.springframework.data.domain.Sort;

public class SortUtilsRuntime {

  private SortUtilsRuntime() {}

  public static Sort toSortRuntime(@Nullable final List<SortField> sorts) {
    List<Sort.Order> orders;

    if (sorts == null || sorts.isEmpty()) {
      orders = List.of();
    } else {
      orders =
          sorts.stream()
              .map(
                  field -> {
                    String property = field.property();
                    Sort.Direction direction = Sort.DEFAULT_DIRECTION;
                    if (null != field.direction()) {
                      String directionString = field.direction();
                      direction =
                          Sort.Direction.fromOptionalString(directionString)
                              .orElse(Sort.DEFAULT_DIRECTION);
                    }
                    return new Sort.Order(direction, property);
                  })
              .toList();
    }

    return Sort.by(orders);
  }
}
