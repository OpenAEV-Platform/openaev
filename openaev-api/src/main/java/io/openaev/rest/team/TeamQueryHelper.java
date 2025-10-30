package io.openaev.rest.team;

import static io.openaev.rest.team.TeamQueryHelper.TeamQueryField.*;
import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.database.model.Team;
import io.openaev.rest.team.output.TeamOutput;
import jakarta.annotation.Nullable;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class TeamQueryHelper {

  public enum TeamQueryField {
    ALL,
    TAGS,
    USERS,
    EXERCISES,
    SCENARIOS,
    ORGANIZATION;

    public static Optional<TeamQueryField> fromString(String value) {
      if (value == null) return Optional.empty();
      try {
        return Optional.of(TeamQueryField.valueOf(value.trim().toUpperCase()));
      } catch (IllegalArgumentException e) {
        return Optional.empty(); // Ignore invalid entries
      }
    }
  }

  private TeamQueryHelper() {}

  // -- SELECT --

  public static void select(
      CriteriaBuilder cb,
      CriteriaQuery<Tuple> cq,
      Root<Team> teamRoot,
      EnumSet<TeamQueryField> includes) {
    List<Selection<?>> selections = new ArrayList<>();

    // Base
    selections.add(teamRoot.get("id").alias("team_id"));
    selections.add(teamRoot.get("name").alias("team_name"));
    selections.add(teamRoot.get("description").alias("team_description"));
    selections.add(teamRoot.get("contextual").alias("team_contextual"));
    selections.add(teamRoot.get("updatedAt").alias("team_updated_at"));

    // Array aggregations
    if (includes != null && !includes.isEmpty()) {
      if (include(includes, TAGS)) {
        Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "tags");
        selections.add(tagIdsExpression.alias("team_tags"));
      }
      if (include(includes, USERS)) {
        Expression<String[]> userIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "users");
        selections.add(userIdsExpression.alias("team_users"));
      }
      if (include(includes, EXERCISES)) {
        Expression<String[]> exerciseIdsExpression =
            createJoinArrayAggOnId(cb, teamRoot, "exercises");
        selections.add(exerciseIdsExpression.alias("team_exercises"));
      }
      if (include(includes, SCENARIOS)) {
        Expression<String[]> scenarioIdsExpression =
            createJoinArrayAggOnId(cb, teamRoot, "scenarios");
        selections.add(scenarioIdsExpression.alias("team_scenarios"));
      }
      if (include(includes, ORGANIZATION)) {
        Expression<String[]> organizationIdExpression =
            createLeftJoin(teamRoot, "organization").get("id");
        selections.add(organizationIdExpression.alias("team_organization"));
      }
    }

    // Multiselect
    cq.multiselect(selections).distinct(true);

    // Group by
    cq.groupBy(Collections.singletonList(teamRoot.get("id")));
  }

  // -- EXECUTION --

  public static List<TeamOutput> execution(
      TypedQuery<Tuple> query, EnumSet<TeamQueryField> includes) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              TeamOutput.TeamOutputBuilder builder =
                  TeamOutput.builder()
                      .id(tuple.get("team_id", String.class))
                      .name(tuple.get("team_name", String.class))
                      .description(tuple.get("team_description", String.class))
                      .contextual(tuple.get("team_contextual", Boolean.class))
                      .updatedAt(tuple.get("team_updated_at", Instant.class));
              if (include(includes, TAGS)) {
                builder.tags(
                    Arrays.stream(tuple.get("team_tags", String[].class))
                        .collect(Collectors.toSet()));
              }
              if (include(includes, USERS)) {
                builder.users(
                    Arrays.stream(tuple.get("team_users", String[].class))
                        .collect(Collectors.toSet()));
              }
              if (include(includes, EXERCISES)) {
                builder.exercises(
                    Arrays.stream(tuple.get("team_exercises", String[].class))
                        .collect(Collectors.toSet()));
              }
              if (include(includes, SCENARIOS)) {
                builder.scenarios(
                    Arrays.stream(tuple.get("team_scenarios", String[].class))
                        .collect(Collectors.toSet()));
              }
              if (include(includes, ORGANIZATION)) {
                builder.organization(tuple.get("team_organization", String.class));
              }
              return builder.build();
            })
        .toList();
  }

  private static boolean include(
      @Nullable final Set<TeamQueryField> includes, @NotBlank final TeamQueryField field) {
    return includes != null && (includes.contains(ALL) || includes.contains(field));
  }
}
