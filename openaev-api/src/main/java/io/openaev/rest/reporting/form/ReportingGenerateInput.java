package io.openaev.rest.reporting.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ReportingFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportingGenerateInput {

  @JsonProperty("reporting_generation_format")
  @NotNull(message = MANDATORY_MESSAGE)
  private ReportingFormat format;
}
