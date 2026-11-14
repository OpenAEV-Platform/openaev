package io.openaev.database.repository;

import io.openaev.database.model.MarkingDefinition;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public interface MarkingDefinitionRepository
    extends CrudRepository<MarkingDefinition, String>, JpaSpecificationExecutor<MarkingDefinition> {

  Optional<MarkingDefinition> findByTypeAndDefinitionAndTenantId(
      String type, String definition, String tenantId);

  List<MarkingDefinition> findAllByTenantId(String tenantId);

  /** The marking definitions among the given ids that belong to the given tenant. */
  @NotNull
  List<MarkingDefinition> findAllByIdInAndTenantId(
      @NotNull Collection<String> ids, @NotNull String tenantId);

  /**
   * The marking definitions among the given ids that belong to the given tenant. Refuses a null id
   * list, as {@code findAllById} does.
   */
  @NotNull
  default List<MarkingDefinition> findAllByIdInTenant(
      Collection<String> ids, @NotNull String tenantId) {
    Assert.notNull(ids, "Ids must not be null");
    return findAllByIdInAndTenantId(ids, tenantId);
  }

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
