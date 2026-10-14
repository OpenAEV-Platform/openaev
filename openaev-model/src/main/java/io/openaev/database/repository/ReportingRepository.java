package io.openaev.database.repository;

import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportingRepository
    extends CrudRepository<Reporting, String>, JpaSpecificationExecutor<Reporting> {

  Optional<Reporting> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<Reporting> findAllByContextTypeAndContextIdOrderByUpdatedAtDesc(
      @NotNull ReportingContextType contextType, @NotNull String contextId);

  List<Reporting> findAllByContextTypeAndContextIdIsNullOrderByUpdatedAtDesc(
      @NotNull ReportingContextType contextType);

  /**
   * Loads the {@code reporting_generations} of the given reportings inside the scoped transaction,
   * for the endpoints that return the raw entity: a lazy collection serialized open-in-view after
   * the commit fails closed to an empty array. Run it in the same session as the query that loaded
   * the reportings: Hibernate fills the collection of the entities it already manages. Generations
   * and schedules are both bags, which one query cannot fetch together, hence one query each.
   */
  @Query("select distinct r from Reporting r left join fetch r.generations where r.id in :ids")
  List<Reporting> findAllByIdInFetchingGenerations(@Param("ids") Collection<String> ids);

  /** Same as {@link #findAllByIdInFetchingGenerations} for {@code reporting_schedules}. */
  @Query("select distinct r from Reporting r left join fetch r.schedules where r.id in :ids")
  List<Reporting> findAllByIdInFetchingSchedules(@Param("ids") Collection<String> ids);
}
