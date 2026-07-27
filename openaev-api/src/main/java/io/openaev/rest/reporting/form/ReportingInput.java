package io.openaev.rest.reporting.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingBranding;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingModule;
import io.openaev.database.model.ReportingTimeRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportingInput {

  @JsonProperty("reporting_name")
  @NotBlank(message = MANDATORY_MESSAGE)
  private String name;

  @JsonProperty("reporting_description")
  private String description;

  @JsonProperty("reporting_context_type")
  @NotNull(message = MANDATORY_MESSAGE)
  private ReportingContextType contextType;

  @JsonProperty("reporting_context_id")
  private String contextId;

  @JsonProperty("reporting_modules")
  private List<ReportingModule> modules;

  @JsonProperty("reporting_branding")
  private ReportingBranding branding;

  @JsonProperty("reporting_default_format")
  private ReportingFormat defaultFormat;

  @JsonProperty("reporting_time_range")
  private ReportingTimeRange timeRange;

  // -- METHOD --

  public Reporting toReporting(@NotNull final Reporting reporting) {
    requireNonNull(reporting, "Reporting must not be null.");

    reporting.setName(this.getName());
    reporting.setDescription(this.getDescription());
    reporting.setContextType(this.getContextType());
    reporting.setContextId(this.getContextId());
    if (this.getModules() != null) {
      reporting.getModules().clear();
      reporting.getModules().addAll(this.getModules());
    }
    reporting.setBranding(this.getBranding());
    if (this.getDefaultFormat() != null) {
      reporting.setDefaultFormat(this.getDefaultFormat());
    }
    if (this.getTimeRange() != null) {
      reporting.setTimeRange(this.getTimeRange());
    }
    return reporting;
  }
}
