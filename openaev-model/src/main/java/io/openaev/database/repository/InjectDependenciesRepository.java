package io.openaev.database.repository;

import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectDependencyId;
import io.openaev.database.model.Injector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectDependenciesRepository
    extends CrudRepository<InjectDependency, InjectDependencyId>,
        JpaSpecificationExecutor<Injector> {

  @Query(
      value =
          "SELECT "
              + "inject_parent_id, "
              + "inject_children_id, "
              + "dependency_condition, "
              + "dependency_created_at, "
              + "dependency_updated_at "
              + "FROM injects_dependencies "
              + "WHERE inject_children_id IN :childrens",
      nativeQuery = true)
  List<InjectDependency> findParents(@NotNull List<String> childrens);

  /**
   * Deletes all inject dependency rows where either side of the dependency belongs to a given
   * scenario. This must be called before deleting the scenario to avoid Hibernate
   * StaleStateException caused by CascadeType.ALL trying to delete the same row twice when multiple
   * injects in the scenario have cross-dependencies.
   *
   * @param scenarioId the scenario whose inject dependencies must be cleared
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM injects_dependencies "
              + "WHERE inject_children_id IN (SELECT inject_id FROM injects i WHERE i.inject_scenario = :scenarioId) "
              + "OR inject_parent_id IN (SELECT inject_id FROM injects i WHERE i.inject_scenario = :scenarioId)",
      nativeQuery = true)
  void deleteAllByScenarioId(@Param("scenarioId") String scenarioId);
}
