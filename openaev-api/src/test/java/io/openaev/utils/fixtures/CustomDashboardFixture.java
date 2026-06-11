package io.openaev.utils.fixtures;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;

import io.openaev.database.model.CustomDashboard;

public class CustomDashboardFixture {

  public static final String NAME = "Custom Dashboard";

  public static CustomDashboard createDefaultCustomDashboard() {
    CustomDashboard customDashboard = CustomDashboard.fromTenant("tenant");
    customDashboard.setName(NAME);
    return customDashboard;
  }

  public static CustomDashboard createCustomDashboardWithDefaultParams() {
    CustomDashboard customDashboard = CustomDashboard.fromTenant("tenant");
    customDashboard.setName(NAME);
    return customDashboard
        .addParameter("Time range", timeRange)
        .addParameter("Start date", startDate)
        .addParameter("End date", endDate);
  }
}
