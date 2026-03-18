package io.openaev.database.repository;

import io.openaev.database.model.StepsDelayQueue;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepDelayQueueRepository extends JpaRepository<StepsDelayQueue, String> {
  Optional<StepsDelayQueue> findFirstByGoalLessThanEqualOrderByGoalAsc(Instant now);
}
