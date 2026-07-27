package io.openaev.rest.generated_report.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.GeneratedReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Input used by the frontend to report progress while it builds the PDF client-side (RUNNING), or
 * to report a client-side generation failure (FAILED). COMPLETED is set implicitly when the final
 * PDF is uploaded via the document endpoint.
 */
@Data
public class GeneratedReportStatusInput {

  @NotNull(message = "Status is mandatory")
  @JsonProperty("generated_report_status")
  private GeneratedReportStatus status;

  @JsonProperty("generated_report_error_message")
  private String errorMessage;
}
