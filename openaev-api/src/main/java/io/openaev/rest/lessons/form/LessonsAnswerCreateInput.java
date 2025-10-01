package io.openaev.rest.lessons.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LessonsAnswerCreateInput {

  @JsonProperty("lessons_answer_score")
  private int score;

  @JsonProperty("lessons_answer_positive")
  private String positive;

  @JsonProperty("lessons_answer_negative")
  private String negative;
}
