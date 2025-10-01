package io.openaev.database.repository;

import io.openaev.database.model.Team;
import io.openaev.database.raw.RawTeam;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository
    extends JpaRepository<Team, String>, StatisticRepository, JpaSpecificationExecutor<Team> {

  @NotNull
  Optional<Team> findById(@NotNull String id);

  @NotNull
  Optional<Team> findByName(@NotNull final String name);

  @NotNull
  List<Team> findAllByNameIgnoreCase(@NotNull final String name);

  @Query(
      "SELECT team FROM Team team where lower(team.name) = lower(:name) and team.contextual = false")
  List<Team> findByNameIgnoreCaseAndNotContextual(@NotNull final String name);

  @Query(
      "select team from Team team where team.organization is null or team.organization.id in :organizationIds")
  List<Team> teamsAccessibleFromOrganizations(
      @Param("organizationIds") List<String> organizationIds);

  @Override
  @Query(
      "select count(distinct u) from User u "
          + "join u.teams as team "
          + "where u.id = :userId and u.createdAt > :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct t) from Team t where t.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "SELECT team_id, team_name "
              + "FROM teams "
              + "WHERE team_id IN :ids ORDER BY team_name;",
      nativeQuery = true)
  List<RawTeam> rawTeamByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT DISTINCT t.team_id AS team_id, t.team_name AS team_name "
              + "FROM teams t "
              + "LEFT JOIN injects_teams it ON t.team_id = it.team_id "
              + "WHERE t.team_id IN (:ids) OR it.inject_id IN (:injectIds) ;",
      nativeQuery = true)
  Set<RawTeam> rawByIdsOrInjectIds(
      @Param("ids") Set<String> ids, @Param("injectIds") Set<String> injectIds);

  @Query(
      value =
          "SELECT teams.team_id, teams.team_name, teams.team_description, teams.team_created_at, teams.team_updated_at, teams.team_organization, "
              + "       team_contextual, "
              + "       coalesce(array_agg(DISTINCT teams_tags.tag_id) FILTER ( WHERE teams_tags.tag_id IS NOT NULL ), '{}') as team_tags, "
              + "       coalesce(array_agg(DISTINCT users_teams.user_id) FILTER ( WHERE users_teams.user_id IS NOT NULL ), '{}') as team_users "
              + "FROM teams "
              + "LEFT JOIN teams_tags ON teams_tags.team_id = teams.team_id "
              + "LEFT JOIN users_teams ON users_teams.team_id = teams.team_id "
              + "GROUP BY teams.team_id ;",
      nativeQuery = true)
  List<RawTeam> rawTeams();

  @Query(
      value =
          "SELECT teams.team_id, teams.team_name, teams.team_description, teams.team_created_at, teams.team_updated_at, teams.team_organization, "
              + "       team_contextual, "
              + "       coalesce(array_agg(DISTINCT teams_tags.tag_id) FILTER ( WHERE teams_tags.tag_id IS NOT NULL ), '{}') as team_tags, "
              + "       coalesce(array_agg(DISTINCT users_teams.user_id) FILTER ( WHERE users_teams.user_id IS NOT NULL ), '{}') as team_users, "
              + "       coalesce(array_agg(DISTINCT exercises_teams.exercise_id) FILTER ( WHERE exercises_teams.exercise_id IS NOT NULL ), '{}') as team_exercises, "
              + "       coalesce(array_agg(DISTINCT scenarios_teams.scenario_id) FILTER ( WHERE scenarios_teams.scenario_id IS NOT NULL ), '{}') as team_scenarios, "
              + "       coalesce(array_agg(DISTINCT injects_expectations.inject_expectation_id) FILTER ( WHERE injects_expectations.inject_expectation_id IS NOT NULL), '{}') as team_expectations, "
              + "       coalesce(array_agg(DISTINCT injects.inject_id) FILTER ( WHERE injects.inject_id IS NOT NULL), '{}') as team_exercise_injects, "
              + "       coalesce(array_agg(DISTINCT communications.communication_id) FILTER ( WHERE communications.communication_id IS NOT NULL), '{}') as team_communications "
              + "FROM teams "
              + "LEFT JOIN teams_tags ON teams_tags.team_id = teams.team_id "
              + "LEFT JOIN users_teams ON users_teams.team_id = teams.team_id "
              + "LEFT JOIN exercises_teams ON exercises_teams.team_id = teams.team_id "
              + "LEFT JOIN scenarios_teams ON scenarios_teams.team_id = teams.team_id "
              + "LEFT JOIN injects_expectations ON injects_expectations.team_id = teams.team_id "
              + "LEFT JOIN exercises ON exercises_teams.exercise_id = exercises.exercise_id "
              + "LEFT JOIN exercises_teams_users ON exercises_teams_users.team_id = teams.team_id "
              + "LEFT JOIN injects ON injects.inject_exercise = exercises.exercise_id "
              + "LEFT JOIN communications ON communications.communication_inject = injects.inject_id "
              + "WHERE teams.team_organization IS NULL OR teams.team_organization IN :organizationIds "
              + "GROUP BY teams.team_id ;",
      nativeQuery = true)
  List<RawTeam> rawTeamsAccessibleFromOrganization(
      @Param("organizationIds") List<String> organizationIds);

  @NotNull
  @EntityGraph(value = "Team.tags", type = EntityGraph.EntityGraphType.LOAD)
  Page<Team> findAll(@NotNull Specification<Team> spec, @NotNull Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT i.inject_exercise, t.team_id, t.team_name "
              + "FROM teams t "
              + "INNER JOIN injects_teams it ON t.team_id = it.team_id "
              + "INNER JOIN injects i ON it.inject_id = i.inject_id "
              + "WHERE i.inject_exercise in :exerciseIds",
      nativeQuery = true)
  List<Object[]> teamsByExerciseIds(Set<String> exerciseIds);

  @Query(
      value =
          "SELECT DISTINCT it.inject_id, t.team_id, t.team_name "
              + "FROM teams t "
              + "INNER JOIN injects_teams it ON t.team_id = it.team_id "
              + "WHERE it.inject_id in :injectIds",
      nativeQuery = true)
  List<Object[]> teamsByInjectIds(Set<String> injectIds);

  @Query(
      "SELECT t FROM Inject i"
          + " JOIN i.teams t"
          + " WHERE ("
          + "   :simulationOrScenarioId is NULL AND i.exercise.id is NULL AND i.scenario.id IS NULL"
          + "   OR (i.exercise.id = :simulationOrScenarioId"
          + "   OR i.scenario.id = :simulationOrScenarioId)"
          + " ) AND (:name IS NULL OR lower(t.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<Team> findAllBySimulationOrScenarioIdAndName(String simulationOrScenarioId, String name);

  @Query(
      value =
          "SELECT DISTINCT t.team_id, t.team_name, t.team_description, t.team_created_at, t.team_updated_at, t.team_organization, t.team_contextual "
              + "FROM teams t "
              + "WHERE EXISTS (SELECT 1 FROM injects_teams it WHERE it.team_id = t.team_id) "
              + "OR EXISTS (SELECT 1 FROM exercises_teams et WHERE et.team_id = t.team_id) "
              + "OR EXISTS (SELECT 1 FROM scenarios_teams st WHERE st.team_id = t.team_id);",
      nativeQuery = true)
  List<Team> findAllTeamsForAtomicTestingsSimulationsAndScenarios();

  @Query(
      value =
          "SELECT t.team_id, t.team_name, t.team_updated_at, t.team_created_at "
              + "FROM teams t "
              + "WHERE t.team_updated_at > :from ORDER BY t.team_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawTeam> findForIndexing(@Param("from") Instant from);
}
