package io.openaev.rest.mapper.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ExportMapperInput {

  @JsonProperty("export_mapper_name")
  private String name;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("ids_to_export")
  private List<String> idsToExport;
}
