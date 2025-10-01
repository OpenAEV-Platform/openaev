package io.openaev.utils.mapper;

import io.openaev.database.raw.RawCustomDashboard;
import io.openaev.rest.custom_dashboard.form.CustomDashboardOutput;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CustomDashboardMapper {

  public CustomDashboardOutput getCustomDashboardOutput(RawCustomDashboard rawCustomDashboard) {
    CustomDashboardOutput customDashboardOutput = new CustomDashboardOutput();
    customDashboardOutput.setId(rawCustomDashboard.getCustom_dashboard_id());
    customDashboardOutput.setName(rawCustomDashboard.getCustom_dashboard_name());
    return customDashboardOutput;
  }

  public List<CustomDashboardOutput> getCustomDashboardOutputs(
      List<RawCustomDashboard> rawCustomDashboards) {
    List<CustomDashboardOutput> customDashboards = new ArrayList<>();
    for (RawCustomDashboard rawCustomDashboard : rawCustomDashboards) {
      customDashboards.add(getCustomDashboardOutput(rawCustomDashboard));
    }
    return customDashboards;
  }
}
