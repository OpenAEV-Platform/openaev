package io.openaev.database.specification;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class TeamSpecification {

  private TeamSpecification() {}

  public static Specification<Team> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Team> teamsAccessibleFromOrganizations(List<String> organizationIds) {
    return (root, query, builder) ->
        builder.or(
            builder.isNull(root.get("organization")),
            root.get("organization").get("id").in(organizationIds));
  }

  public static Specification<Team> contextual(final boolean contextual) {
    if (contextual) {
      return (root, query, builder) -> builder.isTrue(root.get("contextual"));
    }
    return (root, query, builder) -> builder.isFalse(root.get("contextual"));
  }

  public static Specification<Team> fromExercise(@NotBlank final String exerciseId) {
    return (root, query, cb) -> {
      Join<Team, Exercise> exercisesJoin = root.join("exercises", JoinType.LEFT);
      return cb.equal(exercisesJoin.get("id"), exerciseId);
    };
  }

  public static Specification<Team> fromScenario(@NotBlank final String scenarioId) {
    return (root, query, cb) -> {
      Join<Team, Scenario> scenariosJoin = root.join("scenarios", JoinType.LEFT);
      return cb.equal(scenariosJoin.get("id"), scenarioId);
    };
  }

  public static Specification<Team> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
