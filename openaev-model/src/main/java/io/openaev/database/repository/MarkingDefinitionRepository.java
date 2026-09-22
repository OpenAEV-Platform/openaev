package io.openaev.database.repository;

import io.openaev.database.model.MarkingDefinition;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MarkingDefinitionRepository
    extends CrudRepository<MarkingDefinition, String>, JpaSpecificationExecutor<MarkingDefinition> {

  @Query(
      """
      SELECT (count(md) > 0)
      FROM MarkingDefinition md
      WHERE lower(md.type) = lower(:type)
        AND lower(md.definition) = lower(:definition)
        AND md.tenant.id = :tenantId
        AND (:ignoredId IS NULL OR md.id <> :ignoredId)
      """)
  boolean existsByTypeAndDefinitionAndTenantIdExcludingId(
      @Param("type") String type,
      @Param("definition") String definition,
      @Param("tenantId") String tenantId,
      @Param("ignoredId") String ignoredId);

  /**
   * Every marking id defined in the given tenants — the system clearance a background transaction
   * runs at.
   *
   * <p>The tenant predicate is explicit even though the statement inspector would add its own by
   * the time this runs: the caller is in the middle of establishing a scope, and a scope-resolution
   * query that silently depends on the scope it is resolving is the kind of ordering assumption
   * that breaks quietly. Belt and braces, on purpose.
   */
  @Query("select m.id from MarkingDefinition m where m.tenant.id in :tenantIds")
  List<String> findAllIdsByTenantIds(@Param("tenantIds") Collection<String> tenantIds);
}
