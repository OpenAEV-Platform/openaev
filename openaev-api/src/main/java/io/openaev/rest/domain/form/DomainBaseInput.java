package io.openaev.rest.domain.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DomainBaseInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("domain_name")
  @Schema(description = "Name of the domain")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("domain_color")
  @Schema(description = "Color of the domain")
  private String color;
}
