package io.openaev.rest.report.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class ReportInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("report_name")
  private String name;

  @JsonProperty("report_informations")
  private List<ReportInformationInput> reportInformations;

  @JsonProperty("report_global_observation")
  private String globalObservation;
}
