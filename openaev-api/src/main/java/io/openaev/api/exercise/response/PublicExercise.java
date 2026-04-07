package io.openaev.api.exercise.response;

import io.openaev.api.challenge.output.PublicEntity;
import io.openaev.database.model.Exercise;
import lombok.Getter;

@Getter
public class PublicExercise extends PublicEntity {

  public PublicExercise(Exercise exercise) {
    setId(exercise.getId());
    setName(exercise.getName());
    setDescription(exercise.getDescription());
  }
}
