package io.openaev.database.repository;

import io.openaev.database.model.MarkingDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MarkingDefinitionRepository
    extends JpaRepository<MarkingDefinition, String>, JpaSpecificationExecutor<MarkingDefinition> {

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
}
