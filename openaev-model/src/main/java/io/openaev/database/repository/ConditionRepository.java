package io.openaev.database.repository;

import io.openaev.database.model.Condition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConditionRepository  extends JpaRepository<Condition, Long> {
    List<Condition> findAllByStep_Id(String stepId);
}
