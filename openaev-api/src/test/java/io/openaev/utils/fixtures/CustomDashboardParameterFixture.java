package io.openaev.utils.fixtures;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.scenario;
import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.simulation;

import io.openaev.database.model.CustomDashboardParameters;

public class CustomDashboardParameterFixture {

  public static CustomDashboardParameters createSimulationCustomDashboardParameter() {
    CustomDashboardParameters customDashboardParameters = new CustomDashboardParameters();
    customDashboardParameters.setName("simulation_param");
    customDashboardParameters.setType(simulation);
    return customDashboardParameters;
  }

  public static CustomDashboardParameters createScenarioCustomDashboardParameter() {
    CustomDashboardParameters customDashboardParameters = new CustomDashboardParameters();
    customDashboardParameters.setName("scenario_param");
    customDashboardParameters.setType(scenario);
    return customDashboardParameters;
  }
}
