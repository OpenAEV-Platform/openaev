package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import lombok.Data;

@Data
public class InjectsImportInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("sheet_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("import_mapper_id")
  private String importMapperId;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("timezone_offset")
  private Integer timezoneOffset;

  @JsonProperty("launch_date")
  private Date launchDate = null;
}
