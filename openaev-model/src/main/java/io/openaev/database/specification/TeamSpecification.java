package io.openaev.database.specification;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class TeamSpecification {

  private TeamSpecification() {}

  public static Specification<Team> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Team> contextual(final boolean contextual) {
    if (contextual) {
      return (root, query, builder) -> builder.isTrue(root.get("contextual"));
    }
    return (root, query, builder) -> builder.isFalse(root.get("contextual"));
  }

  /**
   * Filter teams that belong to the given exercise.
   *
   * <p>Uses an EXISTS subquery instead of a JOIN to avoid row multiplication when the calling
   * query already has other LEFT JOINs (e.g. users COUNT, tags array_agg). A JOIN on exercises
   * multiplies rows by the number of exercise memberships before GROUP BY collapses them, leading
   * to a significant performance regression on aggregating queries.
   */
  public static Specification<Team> fromExercise(@NotBlank final String exerciseId) {
    return (root, query, cb) -> {
      Subquery<String> subquery = query.subquery(String.class);
      Root<Team> subRoot = subquery.from(Team.class);
      Join<Team, Exercise> exercisesJoin = subRoot.join("exercises", JoinType.INNER);
      subquery
          .select(subRoot.get("id"))
          .where(
              cb.and(
                  cb.equal(subRoot.get("id"), root.get("id")),
                  cb.equal(exercisesJoin.get("id"), exerciseId)));
      return cb.exists(subquery);
    };
  }

  /**
   * Filter teams that belong to the given scenario.
   *
   * <p>Uses an EXISTS subquery for the same reason as {@link #fromExercise}: avoids row
   * multiplication when combined with aggregating joins in the outer query.
   */
  public static Specification<Team> fromScenario(String scenarioId) {
    return (root, query, cb) -> {
      Subquery<String> subquery = query.subquery(String.class);
      Root<Team> subRoot = subquery.from(Team.class);
      Join<Team, Scenario> scenariosJoin = subRoot.join("scenarios", JoinType.INNER);
      subquery
          .select(subRoot.get("id"))
          .where(
              cb.and(
                  cb.equal(subRoot.get("id"), root.get("id")),
                  cb.equal(scenariosJoin.get("id"), scenarioId)));
      return cb.exists(subquery);
    };
  }

  public static Specification<Team> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
