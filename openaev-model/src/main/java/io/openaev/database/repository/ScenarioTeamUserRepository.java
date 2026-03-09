package io.openaev.database.repository;

import io.openaev.database.model.ScenarioTeamUser;
import io.openaev.database.model.ScenarioTeamUserId;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ScenarioTeamUserRepository
    extends JpaRepository<ScenarioTeamUser, ScenarioTeamUserId>,
        CrudRepository<ScenarioTeamUser, ScenarioTeamUserId>,
        JpaSpecificationExecutor<ScenarioTeamUser> {

  @NotNull
  Optional<ScenarioTeamUser> findById(@NotNull final ScenarioTeamUserId id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value = "delete from scenarios_teams_users i where i.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteTeamFromAllReferences(@Param("teamIds") List<String> teamIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "delete from scenarios_teams_users where scenario_id = :scenarioId and team_id in :teamIds",
      nativeQuery = true)
  void deleteByScenarioIdAndTeamIds(
      @Param("scenarioId") String scenarioId, @Param("teamIds") List<String> teamIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "insert into scenarios_teams_users (scenario_id, team_id, user_id) "
              + "values (:scenarioId, :teamId, :userId) "
              + "on conflict (scenario_id, team_id, user_id) do nothing",
      nativeQuery = true)
  void insertIfNotExists(
      @Param("scenarioId") String scenarioId,
      @Param("teamId") String teamId,
      @Param("userId") String userId);
}
