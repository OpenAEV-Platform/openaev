package io.openbas.database.repository;

import io.openbas.database.model.Variable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VariableRepository
    extends CrudRepository<Variable, String>, JpaSpecificationExecutor<Variable> {

  @Query(
      value =
          "INSERT INTO variables(variable_id, variable_key, variable_value, variable_description, variable_type, variable_exercise)"
              + "   SELECT gen_random_uuid(), variable_key, variable_value, variable_description, variable_type, :exerciseId FROM variables as old"
              + "   WHERE old.variable_scenario = :scenarioId",
      nativeQuery = true)
  @Modifying
  void copyVariableFromScenarioForSimulation(String scenarioId, String exerciseId);
}
