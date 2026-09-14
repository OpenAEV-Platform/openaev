package io.openaev.injector_contract.variables.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Exercise;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SimulationContract {
  public static final String VARIABLE_FAMILY = "exercise";

  @VariableContract(
      name = VARIABLE_FAMILY + ".id",
      description = "Id of the simulation in the platform")
  @JsonProperty("exercise_id")
  private final String id;

  @VariableContract(name = VARIABLE_FAMILY + ".name", description = "Name of the simulation")
  @JsonProperty("exercise_name")
  private final String name;

  @VariableContract(
      name = VARIABLE_FAMILY + ".description",
      description = "Description of the simulation")
  @JsonProperty("exercise_description")
  private final String description;

  public static SimulationContract fromSimulation(Exercise exercise) {
    return new SimulationContract(exercise.getId(), exercise.getName(), exercise.getDescription());
  }
}
