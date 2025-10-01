package io.openaev.rest.challenge.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Exercise;
import io.openaev.rest.exercise.response.PublicExercise;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimulationChallengesReader {

  @JsonProperty("exercise_id")
  private String id;

  @JsonProperty("exercise_information")
  private PublicExercise exercise;

  @JsonProperty("exercise_challenges")
  private List<ChallengeInformation> exerciseChallenges = new ArrayList<>();

  public SimulationChallengesReader(Exercise exercise) {
    this.id = exercise.getId();
    this.exercise = new PublicExercise(exercise);
  }
}
