package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.raw.RawOrganization;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The organization list aggregates, for each organization, the injects reaching its members through
 * their teams. Joining the "all teams" injects with an {@code OR} matched every one of them in the
 * tenant for every organization, members or not, and multiplied the rows until the list took over a
 * minute to load (#8926).
 *
 * <p>The tables of the query that are v2-active in production are armed here, so PostgreSQL runs
 * the SQL as {@link io.openaev.config.TenantStatementInspector} rewrites it. Not {@code
 * Transactional}: the scoped-transaction primitive refuses to open inside an active transaction, so
 * the rows are committed with JDBC, which bypasses the inspector, in fresh tenants, and removed in
 * teardown.
 */
@TestPropertySource(
    properties = "openaev.tenant.active-tables=organizations,teams,tags,exercises,scenarios")
@DisplayName("OrganizationRepository")
class OrganizationRepositoryTest extends IntegrationTest {

  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> tenants = new ArrayList<>();
  private final List<String> users = new ArrayList<>();

  private String tenantId;

  @BeforeEach
  void seedTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = insertTenant();
  }

  @AfterEach
  void cleanup() {
    for (String tenant : tenants) {
      String injects = "SELECT inject_id FROM injects WHERE tenant_id = ?";
      String teams = "SELECT team_id FROM teams WHERE tenant_id = ?";
      String organizations = "SELECT organization_id FROM organizations WHERE tenant_id = ?";
      jdbc.update("DELETE FROM injects_teams WHERE inject_id IN (" + injects + ")", tenant);
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM exercises_teams WHERE team_id IN (" + teams + ")", tenant);
      jdbc.update("DELETE FROM scenarios_teams WHERE team_id IN (" + teams + ")", tenant);
      jdbc.update("DELETE FROM users_teams WHERE team_id IN (" + teams + ")", tenant);
      jdbc.update(
          "DELETE FROM organizations_tags WHERE organization_id IN (" + organizations + ")",
          tenant);
      jdbc.update(
          "DELETE FROM users_organizations WHERE organization_id IN (" + organizations + ")",
          tenant);
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM teams WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM organizations WHERE tenant_id = ?", tenant);
      jdbc.update("DELETE FROM tags WHERE tenant_id = ?", tenant);
    }
    for (String user : users) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", user);
    }
    for (String tenant : tenants) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenant);
    }
    tenants.clear();
    users.clear();
  }

  @Nested
  @DisplayName("rawAll injects of an organization")
  class RawAllInjects {

    @Test
    @DisplayName("An organization without members has no inject")
    void given_organizationWithoutMembers_should_haveNoInject() {
      // Arrange
      String organizationId = insertOrganization(tenantId);
      String exerciseId = insertExercise(tenantId);
      insertInject(tenantId, true, "inject_exercise", exerciseId);
      String scenarioId = insertScenario(tenantId);
      insertInject(tenantId, true, "inject_scenario", scenarioId);

      // Act
      RawOrganization organization = rawOrganization(organizationId, List.of(tenantId));

      // Assert
      assertThat(organization.getOrganization_injects()).isNullOrEmpty();
      assertThat(organization.getOrganization_injects_number()).isZero();
      assertThat(organization.getOrganization_tags()).isNullOrEmpty();
    }

    @Test
    @DisplayName(
        "An organization holds, once each, the injects of its members' teams and the all-teams"
            + " injects of their simulations and scenarios")
    void given_membersInTeams_should_listTheInjectsReachingThemOnce() {
      // Arrange
      String organizationId = insertOrganization(tenantId);
      String tagA = insertTag(tenantId);
      String tagB = insertTag(tenantId);
      link("organizations_tags", "organization_id", organizationId, "tag_id", tagA);
      link("organizations_tags", "organization_id", organizationId, "tag_id", tagB);
      String member = insertUser();
      link("users_organizations", "user_id", member, "organization_id", organizationId);
      String teamA = insertTeam(tenantId);
      String teamB = insertTeam(tenantId);
      link("users_teams", "user_id", member, "team_id", teamA);
      link("users_teams", "user_id", member, "team_id", teamB);

      String exerciseId = insertExercise(tenantId);
      link("exercises_teams", "exercise_id", exerciseId, "team_id", teamA);
      String targetingBothTeams = insertInject(tenantId, false, "inject_exercise", exerciseId);
      link("injects_teams", "inject_id", targetingBothTeams, "team_id", teamA);
      link("injects_teams", "inject_id", targetingBothTeams, "team_id", teamB);
      String allTeamsOfExercise = insertInject(tenantId, true, "inject_exercise", exerciseId);

      String scenarioId = insertScenario(tenantId);
      link("scenarios_teams", "scenario_id", scenarioId, "team_id", teamB);
      String allTeamsOfScenario = insertInject(tenantId, true, "inject_scenario", scenarioId);

      // Not reaching the member: an all-teams inject of a simulation without its teams, and an
      // inject targeting a team it is not in.
      String otherExercise = insertExercise(tenantId);
      insertInject(tenantId, true, "inject_exercise", otherExercise);
      String otherTeam = insertTeam(tenantId);
      link("exercises_teams", "exercise_id", exerciseId, "team_id", otherTeam);
      String targetingOtherTeam = insertInject(tenantId, false, "inject_exercise", exerciseId);
      link("injects_teams", "inject_id", targetingOtherTeam, "team_id", otherTeam);

      // Of another tenant, though visible to the caller: the member is also in a team there.
      String otherTenant = insertTenant();
      String teamOfOtherTenant = insertTeam(otherTenant);
      link("users_teams", "user_id", member, "team_id", teamOfOtherTenant);
      String exerciseOfOtherTenant = insertExercise(otherTenant);
      link("exercises_teams", "exercise_id", exerciseOfOtherTenant, "team_id", teamOfOtherTenant);
      String injectOfOtherTenant =
          insertInject(otherTenant, false, "inject_exercise", exerciseOfOtherTenant);
      link("injects_teams", "inject_id", injectOfOtherTenant, "team_id", teamOfOtherTenant);
      insertInject(otherTenant, true, "inject_exercise", exerciseOfOtherTenant);

      // Act
      RawOrganization organization =
          rawOrganization(organizationId, List.of(tenantId, otherTenant));

      // Assert
      assertThat(organization.getOrganization_injects())
          .containsExactlyInAnyOrder(targetingBothTeams, allTeamsOfExercise, allTeamsOfScenario);
      assertThat(organization.getOrganization_injects_number()).isEqualTo(3);
      assertThat(organization.getOrganization_tags()).containsExactlyInAnyOrder(tagA, tagB);
    }
  }

  /**
   * Reads the organizations of {@link #tenantId} under a scope of {@code visibleTenants}: with the
   * other tenant visible, only the query's own tenant conditions keep its teams and injects out.
   */
  private RawOrganization rawOrganization(String organizationId, List<String> visibleTenants) {
    List<RawOrganization> organizations =
        tenantTx.executeReadOnly(
            TxCtx.forTenants(visibleTenants),
            () -> organizationRepository.rawAll(Set.of(tenantId)));
    assertThat(organizations)
        .extracting(RawOrganization::getOrganization_id)
        .containsExactly(organizationId);
    return organizations.getFirst();
  }

  private String insertTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "organization-repository-" + id);
    tenants.add(id);
    return id;
  }

  private String insertOrganization(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO organizations (organization_id, organization_name, tenant_id,"
            + " organization_created_at, organization_updated_at) VALUES (?, ?, ?, now(), now())",
        id,
        "organization-" + id,
        tenant);
    return id;
  }

  private String insertTag(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)", id, "tag-" + id, tenant);
    return id;
  }

  private String insertUser() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        id,
        id + "@organization-repository.test");
    users.add(id);
    return id;
  }

  private String insertTeam(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "team-" + id,
        tenant);
    return id;
  }

  private String insertExercise(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "exercise-" + id,
        tenant);
    return id;
  }

  private String insertScenario(String tenant) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "scenario-" + id,
        tenant);
    return id;
  }

  private String insertInject(
      String tenant, boolean allTeams, String parentColumn, String parentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, tenant_id, "
            + parentColumn
            + ") VALUES (?, ?, ?, true, 0, ?, ?)",
        id,
        "inject-" + id,
        allTeams,
        tenant,
        parentId);
    return id;
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)",
        leftId,
        rightId);
  }
}
