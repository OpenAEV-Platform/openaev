package io.openaev.database.repository;

import io.openaev.database.model.Condition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, Long> {
  List<Condition> findAllByStep_Id(String stepId);
}
