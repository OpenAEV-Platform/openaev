package io.openaev.api.custom_dashboard.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CustomDashboardOutput {

  @JsonProperty("custom_dashboard_id")
  private String id;

  @JsonProperty("custom_dashboard_name")
  private String name;
}
