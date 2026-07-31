package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Tenant-active store for autonomous runs. The tenant predicate on {@code autonomous_runs} is added
 * by the statement inspector, so these derived queries stay tenant-scoped without an explicit
 * clause.
 */
@Repository
public interface AutonomousRunRepository extends JpaRepository<AutonomousRun, String> {

  Optional<AutonomousRun> findBySimulationId(String simulationId);

  boolean existsBySimulationId(String simulationId);

  List<AutonomousRun> findAllByOrderByCreatedAtDesc();
}
