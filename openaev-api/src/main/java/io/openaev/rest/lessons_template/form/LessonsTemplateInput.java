package io.openaev.rest.lessons_template.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LessonsTemplateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("lessons_template_name")
  private String name;

  @JsonProperty("lessons_template_description")
  private String description;
}
