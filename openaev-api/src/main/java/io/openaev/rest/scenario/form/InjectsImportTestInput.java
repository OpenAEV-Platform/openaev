package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.mapper.form.ImportMapperAddInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InjectsImportTestInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("sheet_name")
  private String name;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("import_mapper")
  private ImportMapperAddInput importMapper;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("timezone_offset")
  private Integer timezoneOffset;
}
