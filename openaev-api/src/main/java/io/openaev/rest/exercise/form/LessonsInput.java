package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LessonsInput {

  @JsonProperty("lessons_anonymized")
  private boolean lessonsAnonymized;
}
