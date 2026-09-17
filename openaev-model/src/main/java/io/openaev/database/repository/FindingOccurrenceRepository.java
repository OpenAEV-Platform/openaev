package io.openaev.database.repository;

import io.openaev.database.model.FindingOccurrence;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FindingOccurrenceRepository
    extends JpaRepository<FindingOccurrence, String>, JpaSpecificationExecutor<FindingOccurrence> {

  List<FindingOccurrence> findAllByMigratedFromIdAndTenantId(
      String migratedFromId, String tenantId);

  List<FindingOccurrence> findAllByStableFindingIdAndTenantId(
      String stableFindingId, String tenantId);

  @EntityGraph(
      attributePaths = {
        "stableFinding",
        "inject",
        "inject.exercise",
        "inject.exercise.scenario",
        "inject.injector",
        "migratedFrom",
        "migratedFrom.triage"
      })
  List<FindingOccurrence> findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(
      Collection<String> stableFindingIds, Collection<String> tenantIds);

  @Override
  @EntityGraph(
      attributePaths = {
        "stableFinding",
        "inject",
        "inject.exercise",
        "inject.exercise.scenario",
        "inject.injector",
        "migratedFrom",
        "migratedFrom.triage"
      })
  Page<FindingOccurrence> findAll(
      Specification<FindingOccurrence> specification, Pageable pageable);

  @Query(
      """
      select count(o) > 0 from FindingOccurrence o
      where o.tenant.id = :tenantId
        and o.stableFinding.id = :stableFindingId
        and o.inject.id = :injectId
        and ((:locationType is null and o.locationType is null) or o.locationType = :locationType)
        and ((:locationKey is null and o.locationKey is null) or o.locationKey = :locationKey)
      """)
  boolean existsLiveOccurrence(
      @Param("tenantId") String tenantId,
      @Param("stableFindingId") String stableFindingId,
      @Param("injectId") String injectId,
      @Param("locationType") io.openaev.database.model.FindingLocationType locationType,
      @Param("locationKey") String locationKey);

  Optional<FindingOccurrence>
      findFirstByStableFindingIdAndTenantIdOrderByObservedAtDescInjectIdDescLocationTypeDescLocationKeyDescIdDesc(
          String stableFindingId, String tenantId);
}
