package io.openaev.utils.fixtures;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.rest.team.form.TeamCreateInput;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamFixture {

  public static final String TEAM_NAME = "My team";
  public static final String CONTEXTUAL_TEAM_NAME = "My contextual team";

  public static TeamCreateInput createTeam() {
    TeamCreateInput teamCreateInput = new TeamCreateInput();
    teamCreateInput.setName(TEAM_NAME);
    teamCreateInput.setDescription("Team description");
    return teamCreateInput;
  }

  public static TeamCreateInput createContextualExerciseTeam(List<String> exerciseIds) {
    TeamCreateInput teamCreateInput = new TeamCreateInput();
    teamCreateInput.setName(CONTEXTUAL_TEAM_NAME);
    teamCreateInput.setDescription("Team description");
    teamCreateInput.setContextual(true);
    teamCreateInput.setExerciseIds(exerciseIds);
    return teamCreateInput;
  }

  public static TeamCreateInput createContextualScenarioTeam(List<String> scenarioIds) {
    TeamCreateInput teamCreateInput = new TeamCreateInput();
    teamCreateInput.setName("Scenario team");
    teamCreateInput.setDescription("Team description");
    teamCreateInput.setContextual(true);
    teamCreateInput.setScenarioIds(scenarioIds);
    return teamCreateInput;
  }

  public static Team getTeam(final User user) {
    return getTeam(user, TEAM_NAME, false); // Call the other method with default value
  }

  public static Team getDefaultTeam() {
    return createTeamWithDefaultName();
  }

  public static Team getDefaultContextualTeam() {
    Team team = getDefaultTeam();
    team.setContextual(true);
    return team;
  }

  public static Team getEmptyTeam() {
    return createTeamWithName(TEAM_NAME);
  }

  public static Team getTeam(final User user, String name, Boolean isContextualTeam) {
    Team team = createTeamWithName(name);
    team.setContextual(isContextualTeam);
    if (user != null) {
      team.setUsers(
          new ArrayList<>() {
            {
              add(user);
            }
          });
    }
    return team;
  }

  private static Team createTeamWithDefaultName() {
    return createTeamWithName(null);
  }

  /**
   * Stamps the ambient tenant, which is what {@code TenantBaseListener} did for every team fixture
   * until teams moved to v2. The listener is gone from {@code Team}, so an unattributed team now
   * fails on the not-null tenant column instead; a test that needs another tenant overrides it.
   */
  public static Team createTeamWithName(String name) {
    String new_name = name == null ? "team-%s".formatted(UUID.randomUUID()) : name;
    Team team = new Team();
    team.setName(new_name);
    team.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return team;
  }
}
