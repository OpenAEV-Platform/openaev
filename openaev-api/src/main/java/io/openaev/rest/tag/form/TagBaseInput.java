package io.openaev.rest.tag.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TagBaseInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("tag_name")
  @Schema(description = "Name of the tag")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("tag_color")
  @Schema(description = "Color of the tag")
  private String color;
}
