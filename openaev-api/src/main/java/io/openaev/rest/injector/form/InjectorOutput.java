package io.openaev.rest.injector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.connector.dto.ConnectorOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Schema(description = "Injector output")
public class InjectorOutput extends ConnectorOutput {
  @Schema(description = "Injector id")
  @JsonProperty("injector_id")
  @NotBlank
  private String id;

  @JsonProperty("injector_name")
  @NotBlank
  private String name;

  @JsonProperty("injector_type")
  @NotBlank
  private String type;
}
