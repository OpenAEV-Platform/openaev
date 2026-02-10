package io.openaev.database.repository;

import io.openaev.database.model.Condition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, String> {
  /**
   * Retrieves all {@link Condition} entities associated with the specified step ID.
   *
   * @param stepId the ID of the step to filter conditions by
   * @return a list of conditions linked to the given step ID
   */
  List<Condition> findAllByStep_Id(String stepId);
}
