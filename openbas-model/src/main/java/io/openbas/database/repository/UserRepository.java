package io.openbas.database.repository;

import io.openbas.database.model.User;
import io.openbas.database.raw.RawPlayer;
import io.openbas.database.raw.RawUser;
import io.openbas.database.raw.RawUserAuthFlat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository
    extends JpaRepository<User, String>, JpaSpecificationExecutor<User>, StatisticRepository {

  @NotNull
  Optional<User> findById(@NotNull String id);

  Optional<User> findByEmailIgnoreCase(String email);

  List<User> findAllByEmailInIgnoreCase(List<String> emails);

  @Override
  @Query(
      "select count(distinct u) from User u "
          + "join u.teams as team "
          + "join team.exercises as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and u.createdAt > :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct u) from User u where u.createdAt > :creationDate")
  long globalCount(Instant creationDate);

  @Query("select count(distinct u) from User u")
  long globalCount();

  // -- ADMIN --

  // Custom query to bypass ID generator on User property
  @Modifying
  @Query(
      value =
          "insert into users(user_id, user_firstname, user_lastname, user_email, user_password, user_admin, user_status) "
              + "values (:id, :firstname, :lastName, :email, :password, true, 1)",
      nativeQuery = true)
  void createAdmin(
      @Param("id") String userId,
      @Param("firstname") String userFirstName,
      @Param("lastName") String userLastName,
      @Param("email") String userEmail,
      @Param("password") String userPassword);

  @Query(
      value =
          "select us.*, "
              + "       array_remove(array_agg(tg.tag_id), null) as user_tags,"
              + "       array_remove(array_agg(grp.group_id), null) as user_groups,"
              + "       array_remove(array_agg(tm.team_id), null) as user_teams from users us"
              + "       left join users_groups usr_grp on us.user_id = usr_grp.user_id"
              + "       left join groups grp on usr_grp.group_id = grp.group_id"
              + "       left join users_teams usr_tm on us.user_id = usr_tm.user_id"
              + "       left join teams tm on usr_tm.team_id = tm.team_id"
              + "       left join users_tags usr_tg on us.user_id = usr_tg.user_id"
              + "       left join tags tg on usr_tg.tag_id = tg.tag_id"
              + "      group by us.user_id;",
      nativeQuery = true)
  List<RawUser> rawAll();

  @Query(
      value =
          "SELECT "
              + "us.user_id AS user_id, "
              + "us.user_admin AS user_admin, "
              + "grt.grant_id AS grant_id, "
              + "grt.grant_name AS grant_name, "
              + "grt.grant_resource AS grant_resource, "
              + "grt.grant_resource_type AS grant_resource_type "
              + "FROM users us "
              + "LEFT JOIN users_groups usr_grp ON us.user_id = usr_grp.user_id "
              + "LEFT JOIN grants grt ON grt.grant_group = usr_grp.group_id "
              + "WHERE us.user_id = :userId",
      nativeQuery = true)
  List<RawUserAuthFlat> getUserWithAuth(@Param("userId") String userId);

  @Query(
      value =
          "select us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, "
              + "us.user_country, us.user_organization,"
              + "array_remove(array_agg(tg.tag_id), null) as user_tags "
              + "from users us "
              + "left join users_tags usr_tg on us.user_id = usr_tg.user_id "
              + "left join tags tg on usr_tg.tag_id = tg.tag_id "
              + "group by us.user_id;",
      nativeQuery = true)
  List<RawPlayer> rawAllPlayers();

  @Query(
      value =
          "SELECT us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, "
              + "coalesce(array_agg(DISTINCT exercises_teams.exercise_id) FILTER (WHERE exercises_teams.exercise_id IS NOT NULL), '{}') AS teams_exercises "
              + "FROM users us "
              + "JOIN users_teams ON us.user_id = users_teams.user_id "
              + "JOIN teams ON users_teams.team_id = teams.team_id "
              + "JOIN exercises_teams ON teams.team_id = exercises_teams.team_id "
              + "WHERE exercises_teams.exercise_id = :exerciseId "
              + "GROUP BY us.user_id;",
      nativeQuery = true)
  List<RawPlayer> rawPlayersByExerciseId(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          "select      us.user_id,"
              + "      us.user_firstname,"
              + "      us.user_lastname,"
              + "      us.user_email,"
              + "      us.user_organization from users us where us.user_organization is null or us.user_organization in :organizationIds",
      nativeQuery = true)
  List<RawPlayer> rawPlayersAccessibleFromOrganizations(
      @Param("organizationIds") List<String> organizationIds);

  @Query(
      value =
          "SELECT us.user_id, "
              + "us.user_firstname, "
              + "us.user_lastname, "
              + "us.user_email, "
              + "us.user_phone, "
              + "us.user_organization "
              + "FROM users us "
              + "WHERE us.user_id IN :ids ;",
      nativeQuery = true)
  Set<RawUser> rawUserByIds(@Param("ids") Set<String> ids);

  @Query(
      "SELECT DISTINCT u "
          + "FROM User u "
          + "LEFT JOIN u.groups g "
          + "LEFT JOIN g.roles r "
          + "LEFT JOIN r.capabilities c "
          + "WHERE c IN :capabilities "
          + "OR u.admin = true")
  List<User> adminsOrUsersHavingCapabilities(@Param("capabilities") List<String> capabilities);

  // -- PAGINATION --

  @NotNull
  @EntityGraph(value = "Player.tags-organization", type = EntityGraph.EntityGraphType.LOAD)
  Page<User> findAll(@NotNull Specification<User> spec, @NotNull Pageable pageable);
}
