package io.openaev.database.repository;

import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.ExerciseTeamUserId;
import io.openaev.database.raw.RawExerciseTeamUser;
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
public interface ExerciseTeamUserRepository
    extends JpaRepository<ExerciseTeamUser, ExerciseTeamUserId>,
        CrudRepository<ExerciseTeamUser, ExerciseTeamUserId>,
        JpaSpecificationExecutor<ExerciseTeamUser> {

  @NotNull
  Optional<ExerciseTeamUser> findById(@NotNull ExerciseTeamUserId id);

  @Modifying
  @Query(
      value = "delete from exercises_teams_users i where i.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteTeamsFromAllReferences(@Param("teamIds") List<String> teamIds);

  @Modifying
  @Query(
      value =
          "insert into exercises_teams_users (exercise_id, team_id, user_id) "
              + "values (:exerciseId, :teamId, :userId)",
      nativeQuery = true)
  void addExerciseTeamUser(
      @Param("exerciseId") String exerciseId,
      @Param("teamId") String teamId,
      @Param("userId") String userId);

  /**
   * Idempotent, DB-atomic variant of {@link #addExerciseTeamUser}: inserts the composite link only
   * when it does not already exist, relying on the table's (exercise_id, team_id, user_id) primary
   * key to no-op the conflict. This replaces the check-then-insert (exists...then create) pattern,
   * which two concurrent callbacks in one autonomous decision cycle could both pass, then
   * double-insert and violate the PK - poisoning the enclosing transaction. {@code ON CONFLICT DO
   * NOTHING} makes the enablement safe to run in parallel.
   */
  @Modifying
  @Query(
      value =
          "insert into exercises_teams_users (exercise_id, team_id, user_id) "
              + "values (:exerciseId, :teamId, :userId) on conflict do nothing",
      nativeQuery = true)
  void insertIfAbsent(
      @Param("exerciseId") String exerciseId,
      @Param("teamId") String teamId,
      @Param("userId") String userId);

  @Query(
      value = "SELECT * FROM exercises_teams_users WHERE exercise_id IN :ids ;",
      nativeQuery = true)
  List<RawExerciseTeamUser> rawByExerciseIds(@Param("ids") List<String> ids);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from exercises_teams_users "
              + "where exercise_id = :exerciseId and team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteByExerciseIdAndTeamIds(
      @Param("exerciseId") String exerciseId, @Param("teamIds") Collection<String> teamIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from exercises_teams_users "
              + "where exercise_id = :exerciseId and team_id = :teamId and user_id in :userIds",
      nativeQuery = true)
  @Transactional
  void deleteByExerciseIdAndTeamIdAndUserIds(
      @Param("exerciseId") String exerciseId,
      @Param("teamId") String teamId,
      @Param("userIds") Collection<String> userIds);

  boolean existsByExerciseIdAndTeamIdAndUserId(String exerciseId, String teamId, String userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "delete from exercises_teams_users where team_id = :teamId", nativeQuery = true)
  @Transactional
  void deleteByTeamId(@Param("teamId") String teamId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from exercises_teams_users where team_id = :teamId and user_id not in :userIds",
      nativeQuery = true)
  @Transactional
  void deleteByTeamIdAndUserIdNotIn(
      @Param("teamId") String teamId, @Param("userIds") Collection<String> userIds);
}
