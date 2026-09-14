package io.openaev.utils.fixtures;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;

import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;

public class CustomDashboardFixture {

  public static final String NAME = "Custom Dashboard";

  public static CustomDashboard createDefaultCustomDashboard() {
    CustomDashboard customDashboard = new CustomDashboard();
    customDashboard.setName(NAME);
    customDashboard.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return customDashboard;
  }

  public static CustomDashboard createCustomDashboardWithDefaultParams() {
    CustomDashboard customDashboard = new CustomDashboard();
    customDashboard.setName(NAME);
    customDashboard.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return customDashboard
        .addParameter("Time range", timeRange)
        .addParameter("Start date", startDate)
        .addParameter("End date", endDate);
  }
}
