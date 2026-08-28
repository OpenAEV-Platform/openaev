package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Tenant-active store for autonomous objective templates (built-ins + admin-created). */
@Repository
public interface AutonomousObjectiveTemplateRepository
    extends JpaRepository<AutonomousObjectiveTemplate, String> {

  List<AutonomousObjectiveTemplate> findByEnabledTrueOrderByOrderAsc();

  Optional<AutonomousObjectiveTemplate> findByKey(String key);

  boolean existsByKey(String key);
}
