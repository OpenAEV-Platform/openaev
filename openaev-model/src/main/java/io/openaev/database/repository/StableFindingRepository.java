package io.openaev.database.repository;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.StableFinding;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StableFindingRepository
    extends JpaRepository<StableFinding, String>,
        JpaSpecificationExecutor<StableFinding>,
        StableFindingRepositoryCustom {

  Optional<StableFinding> findByKeyAndTenantId(String key, String tenantId);

  Optional<StableFinding> findByIdAndTenantIdIn(String id, Collection<String> tenantIds);

  /**
   * "Also detected on" (US.4): the other findings with the same type and value, each at its own
   * location, most recently seen first.
   */
  @Query(
      value =
          "SELECT s FROM StableFinding s WHERE s.tenant.id IN :tenantIds AND s.type = :type"
              + " AND s.value = :value AND s.id <> :id ORDER BY s.lastSeen DESC, s.id",
      countQuery =
          "SELECT COUNT(s) FROM StableFinding s WHERE s.tenant.id IN :tenantIds AND s.type = :type"
              + " AND s.value = :value AND s.id <> :id")
  Page<StableFinding> findSiblings(
      @Param("tenantIds") Collection<String> tenantIds,
      @Param("type") ContractOutputType type,
      @Param("value") String value,
      @Param("id") String id,
      Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"sourceInjector", "locationAsset"})
  Page<StableFinding> findAll(Specification<StableFinding> specification, Pageable pageable);
}
