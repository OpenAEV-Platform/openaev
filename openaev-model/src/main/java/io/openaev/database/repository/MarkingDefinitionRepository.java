package io.openaev.database.repository;

import io.openaev.database.model.MarkingDefinition;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Tenant isolation is handled by the v2 statement inspector, so these derived queries carry no
 * tenant predicate of their own — adding one would double-filter.
 */
@Repository
public interface MarkingDefinitionRepository
    extends CrudRepository<MarkingDefinition, String>, JpaSpecificationExecutor<MarkingDefinition> {

  @NotNull
  Optional<MarkingDefinition> findById(@NotNull String id);

  /**
   * Returns a list, not an {@link Optional}: the unique index is composite on {@code (marking_name,
   * tenant_id)}, so several tenants legitimately own a marking of the same name. Any context where
   * the statement inspector is not scoping this table would make a single-result finder throw.
   */
  List<MarkingDefinition> findAllByName(@NotNull String name);

  List<MarkingDefinition> findAllByTypeOrderByOrderAsc(@NotNull String type);
}
