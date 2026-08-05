package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ScopeTeamOutput;
import io.openaev.database.model.Team;

/** Maps {@link Team} entities to {@link ScopeTeamOutput} DTOs. */
public class ScopeTeamMapper {

  private ScopeTeamMapper() {}

  public static ScopeTeamOutput toOutput(Team team) {
    return new ScopeTeamOutput(team.getId(), team.getName());
  }
}
