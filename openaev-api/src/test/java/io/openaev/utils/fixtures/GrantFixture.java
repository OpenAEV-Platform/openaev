package io.openaev.utils.fixtures;

import io.openaev.database.model.*;
import jakarta.annotation.Nullable;

public class GrantFixture {

  public static Grant getGrantForSimulation(Exercise simulation, Grant.GRANT_TYPE grantType) {
    return getGrant(simulation.getId(), Grant.GRANT_RESOURCE_TYPE.SIMULATION, grantType, null);
  }

  public static Grant getGrantForScenario(Scenario scenario) {
    return getGrantForScenario(scenario, Grant.GRANT_TYPE.PLANNER);
  }

  public static Grant getGrantForScenario(Scenario scenario, Grant.GRANT_TYPE grantType) {
    return getGrant(scenario.getId(), Grant.GRANT_RESOURCE_TYPE.SCENARIO, grantType, null);
  }

  public static Grant getGrantForThreatArsenal(String threatArsenalId, Grant.GRANT_TYPE grantType) {
    return getGrant(threatArsenalId, Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL, grantType, null);
  }

  public static Grant getGrant(
      String resourceId,
      Grant.GRANT_RESOURCE_TYPE resourceType,
      Grant.GRANT_TYPE grantType,
      @Nullable Group group) {
    Grant grant = new Grant();
    grant.setName(grantType);
    grant.setResourceId(resourceId);
    grant.setGrantResourceType(resourceType);
    if(group != null){
      grant.setGroup(group);
    }
    return grant;
  }
}
