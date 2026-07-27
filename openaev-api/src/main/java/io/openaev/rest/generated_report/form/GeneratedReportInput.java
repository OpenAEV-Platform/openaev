package io.openaev.rest.generated_report.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.GeneratedReportTemplate;
import io.openaev.database.model.GeneratedReportTriggerSource;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Input used to trigger the generation of a new structured report. */
@Data
public class GeneratedReportInput {

  @NotNull(message = "Template is mandatory")
  @JsonProperty("generated_report_template")
  private GeneratedReportTemplate template;

  @JsonProperty("generated_report_trigger_source")
  private GeneratedReportTriggerSource triggerSource = GeneratedReportTriggerSource.MANUAL;

  /** Human-readable scope summary shown in "Access Reports" history (e.g. comparison window). */
  @JsonProperty("generated_report_label")
  private String label;
}
