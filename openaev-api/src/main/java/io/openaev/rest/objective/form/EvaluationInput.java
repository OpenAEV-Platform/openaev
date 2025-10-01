package io.openaev.rest.objective.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EvaluationInput {

  @JsonProperty("evaluation_score")
  private Long score;

  public Long getScore() {
    return score;
  }

  public void setScore(Long score) {
    this.score = score;
  }
}
