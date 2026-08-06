package io.openaev.rest.injector.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class InjectorSimple {

  @Schema(description = "Injector Id")
  @JsonProperty("injector_id")
  @NotBlank
  private String id;

  @Schema(description = "Injector Name")
  @JsonProperty("injector_name")
  @NotBlank
  private String name;

  @Schema(description = "Injector Type")
  @JsonProperty("injector_type")
  private String type;
}
