package io.openaev.injector_contract.variables.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Exercise;

public record SimulationContract(
    @VariableContract(
            name = VARIABLE_FAMILY + ".id",
            description = "Id of the simulation in the platform")
        @JsonProperty("exercise_id")
        String simulationId,
    @VariableContract(name = VARIABLE_FAMILY + ".name", description = "Name of the simulation")
        @JsonProperty("exercise_name")
        String simulationName,
    @VariableContract(
            name = VARIABLE_FAMILY + ".description",
            description = "Description of the simulation")
        @JsonProperty("exercise_description")
        String simulationDescription) {
  public static final String VARIABLE_FAMILY = "exercise";

  public static SimulationContract fromSimulation(Exercise exercise) {
    return new SimulationContract(exercise.getId(), exercise.getName(), exercise.getDescription());
  }
}
