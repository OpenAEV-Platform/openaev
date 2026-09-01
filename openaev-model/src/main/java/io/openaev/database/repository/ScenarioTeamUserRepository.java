package io.openaev.database.repository;

import io.openaev.database.model.ScenarioTeamUser;
import io.openaev.database.model.ScenarioTeamUserId;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
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

  @Modifying
  @Query(
      value = "delete from scenarios_teams_users i where i.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteTeamFromAllReferences(@Param("teamIds") List<String> teamIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from scenarios_teams_users "
              + "where scenario_id = :scenarioId and team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteByScenarioIdAndTeamIds(
      @Param("scenarioId") String scenarioId, @Param("teamIds") Collection<String> teamIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "delete from scenarios_teams_users where team_id = :teamId", nativeQuery = true)
  @Transactional
  void deleteByTeamId(@Param("teamId") String teamId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from scenarios_teams_users where team_id = :teamId and user_id not in :userIds",
      nativeQuery = true)
  @Transactional
  void deleteByTeamIdAndUserIdNotIn(
      @Param("teamId") String teamId, @Param("userIds") Collection<String> userIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from scenarios_teams_users "
              + "where scenario_id = :scenarioId and team_id = :teamId and user_id in :userIds",
      nativeQuery = true)
  @Transactional
  void deleteByScenarioIdAndTeamIdAndUserIds(
      @Param("scenarioId") String scenarioId,
      @Param("teamId") String teamId,
      @Param("userIds") Collection<String> userIds);

  /**
   * Uses ON CONFLICT to atomically avoid duplicate composite links; table is a pure join and has no
   * index/audit/stream side effect chain.
   */
  @Modifying
  @Query(
      value =
          "insert into scenarios_teams_users (scenario_id, team_id, user_id) "
              + "values (:scenarioId, :teamId, :userId) on conflict do nothing",
      nativeQuery = true)
  void insertIfAbsent(
      @Param("scenarioId") String scenarioId,
      @Param("teamId") String teamId,
      @Param("userId") String userId);

  boolean existsByScenarioIdAndTeamIdAndUserId(String scenarioId, String teamId, String userId);
}
