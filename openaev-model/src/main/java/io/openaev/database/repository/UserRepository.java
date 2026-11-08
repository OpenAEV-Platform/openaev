package io.openaev.database.repository;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawNewsFeedRecipient;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.raw.RawUser;
import io.openaev.database.raw.RawUserAuthFlat;
import io.openaev.database.raw.RawUserIdentity;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
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

  long countByIdIn(Set<String> ids);

  Optional<User> findByEmailIgnoreCase(String email);

  @Query(
      "SELECT DISTINCT u FROM User u JOIN u.tenants t WHERE LOWER(u.email) IN :emails AND t.id = :tenantId")
  List<User> findAllByEmailInAndTenantIdNormalized(
      @Param("emails") List<String> emails, @Param("tenantId") String tenantId);

  default List<User> findAllByEmailInIgnoreCaseAndTenantId(List<String> emails, String tenantId) {
    if (emails == null || emails.isEmpty()) {
      return List.of();
    }
    List<String> normalizedEmails =
        emails.stream().map(email -> email.toLowerCase(Locale.ROOT)).toList();
    return findAllByEmailInAndTenantIdNormalized(normalizedEmails, tenantId);
  }

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
          "select us.user_id, us.user_firstname, us.user_lastname, us.user_email, us.user_phone, "
              // The organization of the tenant, read from the join table: the legacy column of "us"
              // is no longer maintained.
              + "       (select uo.organization_id from users_organizations uo "
              + "         join organizations org on org.organization_id = uo.organization_id "
              + "         where uo.user_id = us.user_id and org.tenant_id = :tenantId "
              + "         order by uo.organization_id limit 1) as user_organization, "
              + "       array_remove(array_agg(tg.tag_id), null) as user_tags,"
              + "       array_remove(array_agg(grp.group_id), null) as user_groups,"
              + "       array_remove(array_agg(tm.team_id), null) as user_teams from users us"
              + "       join users_tenants ut on us.user_id = ut.user_id"
              + "       left join users_groups usr_grp on us.user_id = usr_grp.user_id"
              + "       left join groups grp on usr_grp.group_id = grp.group_id"
              + "       left join users_teams usr_tm on us.user_id = usr_tm.user_id"
              + "       left join teams tm on usr_tm.team_id = tm.team_id"
              + "       left join users_tags usr_tg on us.user_id = usr_tg.user_id"
              + "       left join tags tg on usr_tg.tag_id = tg.tag_id"
              + "      where ut.tenant_id = :tenantId"
              + "      group by us.user_id;",
      nativeQuery = true)
  List<RawUser> rawAllInTenant(@Param("tenantId") String tenantId);

  @Query(
      value = "select ut.user_id from users_tenants ut where ut.tenant_id = :tenantId",
      nativeQuery = true)
  List<String> findUserIdsByTenantId(@Param("tenantId") String tenantId);

  /**
   * Returns a projection rather than {@code User} entities, because loading one user also loads its
   * groups (EAGER), then each group's grants, roles and other members, and the audit listener
   * snapshots every Group and Role loaded to JSON. That is a lot of work to display a name.
   */
  @Query(
      value =
          "select us.user_id, us.user_email, us.user_firstname, us.user_lastname"
              + " from users us where us.user_id in (:ids)",
      nativeQuery = true)
  List<RawUserIdentity> rawIdentities(@Param("ids") Collection<String> ids);

  @Query(
      value = "select us.user_id, us.user_email, us.user_firstname, us.user_lastname from users us",
      nativeQuery = true)
  List<RawUserIdentity> rawAllIdentities();

  /**
   * Users who receive the XTM Hub news feed: the platform users of at least one of the given
   * tenants, that is admins and members of a group of these tenants or of the platform. Players
   * (users only known through their teams, who never log in) and reserved service/connector
   * accounts are left out. A projection rather than {@code User} entities (see {@link
   * #rawIdentities}).
   */
  @Query(
      "select u.id as id, u.unsubscribedNewsFeedTypes as unsubscribedTypes from User u"
          + " where exists (select t.id from u.tenants t where t.id in :tenantIds)"
          + " and lower(u.email) not like '%@openaev.invalid'"
          + " and (u.admin = true or exists (select g.id from u.groups g"
          + " where g.tenant is null or g.tenant.id in :tenantIds))")
  List<RawNewsFeedRecipient> rawNewsFeedRecipients(
      @Param("tenantIds") Collection<String> tenantIds);

  @Query(
      value = "select ut.tenant_id from users_tenants ut where ut.user_id = :userId",
      nativeQuery = true)
  List<String> findTenantIdsByUserId(@Param("userId") String userId);

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
              + "us.user_country, us.user_phone, us.user_admin, "
              // A user holds one organization per tenant: a correlated sub-query keeps the current
              // tenant's one only, without a join that would split a multi-tenant user in groups.
              + "(select uo.organization_id from users_organizations uo "
              + "  join organizations org on org.organization_id = uo.organization_id "
              + "  where uo.user_id = us.user_id "
              + "  and org.tenant_id = :#{#tenantContext.currentTenant} "
              + "  order by uo.organization_id limit 1) as user_organization, "
              + "array_remove(array_agg(tg.tag_id), null) as user_tags "
              + "from users us "
              + "left join users_tags usr_tg on us.user_id = usr_tg.user_id "
              + "left join tags tg on usr_tg.tag_id = tg.tag_id "
              + "left join users_tenants ut on us.user_id = ut.user_id "
              + "where ut.tenant_id = :#{#tenantContext.currentTenant} "
              // Reserved service/connector accounts are system users, never players.
              + "and us.user_email not ilike '%@openaev.invalid' "
              + "group by us.user_id;",
      nativeQuery = true)
  List<RawPlayer> rawAllPlayers();

  @Query(
      value =
          "SELECT us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, us.user_admin "
              + "FROM users us "
              + "JOIN users_teams ON us.user_id = users_teams.user_id "
              + "JOIN teams ON users_teams.team_id = teams.team_id "
              + "JOIN exercises_teams ON teams.team_id = exercises_teams.team_id "
              + "WHERE exercises_teams.exercise_id = :exerciseId "
              + "UNION "
              + "SELECT us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, us.user_admin "
              + "FROM users us "
              + "JOIN evaluations ev ON us.user_id = ev.evaluation_user "
              + "JOIN objectives ob ON ob.objective_id = ev.evaluation_objective "
              + "WHERE ob.objective_exercise = :exerciseId;",
      nativeQuery = true)
  List<RawPlayer> rawPlayersByExerciseId(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          "SELECT us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, us.user_admin "
              + "FROM users us "
              + "JOIN users_teams ON us.user_id = users_teams.user_id "
              + "JOIN teams ON users_teams.team_id = teams.team_id "
              + "JOIN scenarios_teams ON teams.team_id = scenarios_teams.team_id "
              + "WHERE scenarios_teams.scenario_id = :scenarioId "
              + "UNION "
              + "SELECT us.user_id, us.user_email, "
              + "us.user_firstname, us.user_lastname, us.user_admin "
              + "FROM users us "
              + "JOIN evaluations ev ON us.user_id = ev.evaluation_user "
              + "JOIN objectives ob ON ob.objective_id = ev.evaluation_objective "
              + "WHERE ob.objective_scenario = :scenarioId;",
      nativeQuery = true)
  List<RawPlayer> rawPlayersByScenarioId(@Param("scenarioId") String scenarioId);

  @Query(
      "SELECT DISTINCT u "
          + "FROM User u "
          + "JOIN u.tenants ut "
          + "LEFT JOIN u.groups g "
          + "LEFT JOIN g.roles r "
          + "LEFT JOIN r.capabilities c "
          + "WHERE ut.id = :#{#tenantContext.currentTenant} "
          + "AND (u.admin = true OR c IN :capabilities)")
  List<User> adminsOrUsersHavingCapabilities(@Param("capabilities") List<String> capabilities);

  // -- Tenant-scoped --

  @Query("SELECT u FROM User u JOIN u.tenants t WHERE u.id IN :ids AND t.id = :tenantId")
  List<User> findAllByIdInAndTenantId(
      @Param("ids") Collection<String> ids, @Param("tenantId") String tenantId);

  // -- PAGINATION --

  @NotNull
  Page<User> findAll(@NotNull Specification<User> spec, @NotNull Pageable pageable);

  @Query(
      value =
          "SELECT us.* "
              + "FROM users us "
              + "JOIN tokens t ON us.user_id = t.token_user "
              + "JOIN users_tenants ut ON us.user_id = ut.user_id "
              + "WHERE t.token_value = :token "
              + "AND ut.tenant_id = :tenantId "
              + "LIMIT 1",
      nativeQuery = true)
  Optional<User> findByTokenAndTenantId(
      @Param("token") String token, @Param("tenantId") String tenantId);

  @Query("SELECT u FROM User u JOIN Token t ON u.id = t.user.id WHERE t.value = :token")
  Optional<User> findByToken(@Param("token") String token);

  // -- ORGANIZATIONS --

  /**
   * Sets the organization of a user in a tenant, leaving the ones it holds in other tenants
   * untouched, and keeps the in-memory view of {@code user} in sync. Must run in the caller's
   * transaction, and the user must already be saved.
   *
   * <p>It is a SQL write rather than a change of {@code User#organizations}: Hibernate only knows
   * the organizations in the scope of the transaction, so clearing the last one would delete every
   * row of the user, the ones of the tenants out of scope included. The row of the user is locked
   * first, so two concurrent assignments in a tenant cannot both insert a different organization.
   *
   * @param user the saved user
   * @param tenantId the tenant whose organization is replaced
   * @param organization the new organization of that tenant, or {@code null} to clear it
   */
  default void assignOrganization(User user, String tenantId, Organization organization) {
    Organization previous = user.organizationOfTenant(tenantId);
    boolean changed =
        !Objects.equals(
            previous == null ? null : previous.getId(),
            organization == null ? null : organization.getId());
    // In-memory view first: the queries below flush the pending profile changes of the user, and
    // the update event published by that flush serializes the user as it is at that moment.
    user.setOrganizationForTenant(tenantId, organization);
    if (changed) {
      // The join table is written in SQL, so the user row would stay untouched and no update event
      // would reach the connected clients: marking the user updated makes Hibernate publish it.
      user.setUpdatedAt(Instant.now());
    }
    lockRow(user.getId());
    removeOrganizationsOfTenant(user.getId(), tenantId);
    if (organization != null) {
      addOrganization(user.getId(), organization.getId());
    }
  }

  // Native: JPA only exposes FOR UPDATE (PESSIMISTIC_WRITE), which would block the inserts of rows
  // referencing the user (tags, tokens...); FOR NO KEY UPDATE does not. Unlike a no-op UPDATE it
  // writes no tuple and fires no UPDATE trigger. No entity is read or changed, so no listener,
  // index or audit side effect is lost.
  @Query(
      value = "SELECT us.user_id FROM users us WHERE us.user_id = :userId FOR NO KEY UPDATE",
      nativeQuery = true)
  List<String> lockRow(@Param("userId") String userId);

  // Native: a set-based delete scoped by tenant through a sub-query. It touches a join table that
  // is not indexed, audited nor streamed, so no session side effect is lost.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "DELETE FROM users_organizations WHERE user_id = :userId AND organization_id IN ("
              + "SELECT organization_id FROM organizations WHERE tenant_id = :tenantId)",
      nativeQuery = true)
  void removeOrganizationsOfTenant(
      @Param("userId") String userId, @Param("tenantId") String tenantId);

  // Native: atomic upsert (ON CONFLICT) on a join table that is not indexed, audited nor streamed.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO users_organizations (user_id, organization_id)"
              + " VALUES (:userId, :organizationId) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addOrganization(
      @Param("userId") String userId, @Param("organizationId") String organizationId);

  // Native: atomic upsert (ON CONFLICT) on a join table that is not indexed, audited nor streamed.
  // It adds one link without rewriting the association: Hibernate only knows the tags in the scope
  // of the transaction, so replacing the collection would delete the links to the ones out of it.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO users_tags (user_id, tag_id) VALUES (:userId, :tagId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addTag(@Param("userId") String userId, @Param("tagId") String tagId);

  // JPQL, not the loaded collection: the collection may have been read before the row of the user
  // was locked, while this reads the links as committed once the lock is held. Rooted on Tag, since
  // selecting the id through the join table alone never reads the tags table: only the tags in the
  // scope of the transaction come back.
  @Query(
      "SELECT t.id FROM Tag t WHERE t.id IN"
          + " (SELECT linked.id FROM User u JOIN u.tags linked WHERE u.id = :userId)")
  List<String> tagIdsInScope(@Param("userId") String userId);

  // Native: a set-based delete of the listed links only, on a join table that is not indexed,
  // audited nor streamed. Replacing the collection would delete every row of the user, the links
  // to the tags the transaction's scope cannot see included.
  @Modifying(flushAutomatically = true)
  @Query(
      value = "DELETE FROM users_tags WHERE user_id = :userId AND tag_id IN :tagIds",
      nativeQuery = true)
  void removeTags(@Param("userId") String userId, @Param("tagIds") Collection<String> tagIds);

  // Native: same rationale as addTag, for the teams of the user.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO users_teams (user_id, team_id) VALUES (:userId, :teamId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void addTeam(@Param("userId") String userId, @Param("teamId") String teamId);

  // -- DELETE --

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM users u WHERE u.user_id = :userId", nativeQuery = true)
  int deleteByIdNative(@Param("userId") String userId);
}
