package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Tenant-active store for autonomous objective templates (built-ins + admin-created).
 *
 * <p>The table is tenant-active, so every read here is rewritten by the statement inspector and
 * returns only the rows inside the current scope. {@code findByKey} relies on that: the only unique
 * business key is {@code (tenant_id, key)}, so the lookup is single-valued under a single-tenant
 * scope and ambiguous under a broader one.
 */
@Repository
public interface AutonomousObjectiveTemplateRepository
    extends CrudRepository<AutonomousObjectiveTemplate, String> {

  List<AutonomousObjectiveTemplate> findByEnabledTrueOrderByOrderAsc();

  Optional<AutonomousObjectiveTemplate> findByKey(String key);
}
