package io.openaev.rest.mapper.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportMapperUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("import_mapper_name")
  private String name;

  @Pattern(regexp = "^[A-Z]{1,2}$")
  @JsonProperty("import_mapper_inject_type_column")
  @NotBlank
  private String injectTypeColumn;

  @JsonProperty("import_mapper_inject_importers")
  @NotNull
  private List<InjectImporterUpdateInput> importers = new ArrayList<>();
}
