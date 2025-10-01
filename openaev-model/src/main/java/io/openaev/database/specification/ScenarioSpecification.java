package io.openaev.database.specification;

import io.openaev.database.model.Scenario;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public class ScenarioSpecification {

  private ScenarioSpecification() {}

  public static Specification<Scenario> isRecurring() {
    return (root, query, cb) -> cb.isNotNull(root.get("recurrence"));
  }

  public static Specification<Scenario> noRecurring() {
    return (root, query, cb) -> cb.isNull(root.get("recurrence"));
  }

  public static Specification<Scenario> recurrenceStartDateBefore(
      @NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.lessThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<Scenario> recurrenceStopDateAfter(@NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.greaterThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<Scenario> recurrenceStopDateBefore(@NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.lessThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<Scenario> findGrantedFor(String userId) {
    return (root, query, criteriaBuilder) -> {
      Path<Object> path = root.join("grants").join("group").join("users").get("id");
      return criteriaBuilder.equal(path, userId);
    };
  }

  public static Specification<Scenario> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
