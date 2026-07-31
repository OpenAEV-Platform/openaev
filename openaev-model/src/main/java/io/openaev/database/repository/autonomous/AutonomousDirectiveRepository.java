package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousDirectiveStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Store for operator steering directives injected into a live autonomous run. */
@Repository
public interface AutonomousDirectiveRepository
    extends JpaRepository<AutonomousDirective, String> {

  List<AutonomousDirective> findByRunIdAndStatusOrderByCreatedAtAsc(
      String runId, AutonomousDirectiveStatus status);

  List<AutonomousDirective> findByRunIdOrderByCreatedAtAsc(String runId);
}
