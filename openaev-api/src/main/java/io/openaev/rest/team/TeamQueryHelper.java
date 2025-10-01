package io.openaev.rest.team;

import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.database.model.Team;
import io.openaev.rest.team.output.TeamOutput;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TeamQueryHelper {

  private TeamQueryHelper() {}

  // -- SELECT --

  public static void select(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<Team> teamRoot) {
    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "tags");
    Expression<String[]> userIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "users");
    Expression<String[]> exerciseIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "exercises");
    Expression<String[]> scenarioIdsExpression = createJoinArrayAggOnId(cb, teamRoot, "scenarios");
    Expression<String[]> organizationIdExpression =
        createLeftJoin(teamRoot, "organization").get("id");

    // Multiselect
    cq.multiselect(
            teamRoot.get("id").alias("team_id"),
            teamRoot.get("name").alias("team_name"),
            teamRoot.get("description").alias("team_description"),
            teamRoot.get("contextual").alias("team_contextual"),
            teamRoot.get("updatedAt").alias("team_updated_at"),
            tagIdsExpression.alias("team_tags"),
            userIdsExpression.alias("team_users"),
            organizationIdExpression.alias("team_organization"),
            exerciseIdsExpression.alias("team_exercises"),
            scenarioIdsExpression.alias("team_scenarios"))
        .distinct(true);

    // Group by
    cq.groupBy(Collections.singletonList(teamRoot.get("id")));
  }

  // -- EXECUTION --

  public static List<TeamOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                TeamOutput.builder()
                    .id(tuple.get("team_id", String.class))
                    .name(tuple.get("team_name", String.class))
                    .description(tuple.get("team_description", String.class))
                    .contextual(tuple.get("team_contextual", Boolean.class))
                    .updatedAt(tuple.get("team_updated_at", Instant.class))
                    .exercises(
                        Arrays.stream(tuple.get("team_exercises", String[].class))
                            .collect(Collectors.toSet()))
                    .scenarios(
                        Arrays.stream(tuple.get("team_scenarios", String[].class))
                            .collect(Collectors.toSet()))
                    .tags(
                        Arrays.stream(tuple.get("team_tags", String[].class))
                            .collect(Collectors.toSet()))
                    .users(
                        Arrays.stream(tuple.get("team_users", String[].class))
                            .collect(Collectors.toSet()))
                    .organization(tuple.get("team_organization", String.class))
                    .build())
        .toList();
  }
}
