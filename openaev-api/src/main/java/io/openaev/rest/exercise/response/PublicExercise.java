package io.openaev.rest.exercise.response;

import io.openaev.database.model.Exercise;
import io.openaev.rest.challenge.output.PublicEntity;
import lombok.Getter;

@Getter
public class PublicExercise extends PublicEntity {

  public PublicExercise(Exercise exercise) {
    setId(exercise.getId());
    setName(exercise.getName());
    setDescription(exercise.getDescription());
  }
}
