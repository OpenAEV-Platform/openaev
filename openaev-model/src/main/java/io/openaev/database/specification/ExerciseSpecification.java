package io.openaev.database.specification;

import static io.openaev.database.model.ExerciseStatus.FINISHED;
import static io.openaev.database.model.ExerciseStatus.SCHEDULED;

import io.openaev.database.model.Exercise;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Path;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class ExerciseSpecification {

  private ExerciseSpecification() {}

  public static Specification<Exercise> recurringInstanceNotStarted() {
    return (root, query, cb) ->
        cb.and(cb.equal(root.get("status"), SCHEDULED), cb.isNotNull(root.get("scenario")));
  }

  public static Specification<Exercise> findGrantedFor(@NotNull final String userId) {
    return (root, query, cb) -> {
      Path<Object> path = root.join("grants").join("group").join("users").get("id");
      return cb.equal(path, userId);
    };
  }

  public static Specification<Exercise> fromScenario(@NotNull final String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }

  public static Specification<Exercise> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }

  // -- BASIC PROPERTY --

  public static Specification<Exercise> finished() {
    return (root, query, cb) -> cb.equal(root.get("status"), FINISHED);
  }

  public static Specification<Exercise> closestBefore(@NotNull final Instant instant) {
    return (root, query, cb) -> {
      assert query != null;
      query.orderBy(cb.desc(root.get("end")));
      return cb.lessThan(root.get("end"), instant);
    };
  }
}
