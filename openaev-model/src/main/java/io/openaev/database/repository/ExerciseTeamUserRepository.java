package io.openaev.database.repository;

import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.ExerciseTeamUserId;
import io.openaev.database.raw.RawExerciseTeamUser;
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
public interface ExerciseTeamUserRepository
    extends JpaRepository<ExerciseTeamUser, ExerciseTeamUserId>,
        CrudRepository<ExerciseTeamUser, ExerciseTeamUserId>,
        JpaSpecificationExecutor<ExerciseTeamUser> {

  @NotNull
  Optional<ExerciseTeamUser> findById(@NotNull ExerciseTeamUserId id);

  @Modifying
  @Query(
      value = "delete from exercises_teams_users i where i.user_id = :userId",
      nativeQuery = true)
  void deleteUserFromAllReferences(@Param("userId") String userId);

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

  @Query(value = "SELECT * FROM exercises_teams_users WHERE team_id IN :ids ;", nativeQuery = true)
  List<RawExerciseTeamUser> rawByTeamIds(@Param("ids") List<String> ids);

  @Query(
      value = "SELECT * FROM exercises_teams_users WHERE exercise_id IN :ids ;",
      nativeQuery = true)
  List<RawExerciseTeamUser> rawByExerciseIds(@Param("ids") List<String> ids);
}
