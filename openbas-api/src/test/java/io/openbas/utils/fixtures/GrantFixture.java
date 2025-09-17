package io.openbas.utils.fixtures;

import io.openbas.database.model.*;

public class GrantFixture {

  public static Grant getGrantForSimulation(Exercise simulation) {
    return getGrantForSimulation(simulation, Grant.GRANT_TYPE.PLANNER);
  }

  public static Grant getGrantForSimulation(Exercise simulation, Grant.GRANT_TYPE grantType) {
    Grant grant = new Grant();
    grant.setName(grantType);
    grant.setResourceId(simulation.getId());
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
    return grant;
  }

  public static Grant getGrantForSimulation(Exercise simulation, Group group) {
    Grant grant = new Grant();
    grant.setName(Grant.GRANT_TYPE.PLANNER);
    grant.setResourceId(simulation.getId());
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
    grant.setGroup(group);
    return grant;
  }

  public static Grant getGrantForScenario(Scenario scenario) {
    return getGrantForScenario(scenario, Grant.GRANT_TYPE.PLANNER);
  }

  public static Grant getGrantForScenario(Scenario scenario, Group group) {
    Grant grant = new Grant();
    grant.setName(Grant.GRANT_TYPE.PLANNER);
    grant.setResourceId(scenario.getId());
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    grant.setGroup(group);
    return grant;
  }

  public static Grant getGrantForScenario(Scenario scenario, Grant.GRANT_TYPE grantType) {
    Grant grant = new Grant();
    grant.setName(grantType);
    grant.setResourceId(scenario.getId());
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
    return grant;
  }

  public static Grant getGrant(
      String resourceId,
      Grant.GRANT_RESOURCE_TYPE resourceType,
      Grant.GRANT_TYPE grantType,
      Group group) {
    Grant grant = new Grant();
    grant.setName(grantType);
    grant.setResourceId(resourceId);
    grant.setGrantResourceType(resourceType);
    grant.setGroup(group);
    return grant;
  }

  public static Grant getGrantForPayload(Payload payload, Grant.GRANT_TYPE grantType) {
    Grant grant = new Grant();
    grant.setName(grantType);
    grant.setResourceId(payload.getId());
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.PAYLOAD);
    return grant;
  }
}
