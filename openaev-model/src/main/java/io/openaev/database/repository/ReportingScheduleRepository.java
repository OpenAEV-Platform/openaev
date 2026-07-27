package io.openaev.database.repository;

import io.openaev.database.model.ReportingSchedule;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportingScheduleRepository
    extends CrudRepository<ReportingSchedule, String>, JpaSpecificationExecutor<ReportingSchedule> {

  Optional<ReportingSchedule> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<ReportingSchedule> findAllByEnabledTrue();

  List<ReportingSchedule> findAllByReportingId(@NotNull String reportingId);

  /**
   * Loads every enabled schedule with the associations used by the scheduling engine after the
   * session closes (reporting, owner, tenant, recipient users) fully initialized. Cross-tenant by
   * design: the caller disables the tenant filter.
   */
  @Query(
      "select distinct s from ReportingSchedule s "
          + "join fetch s.reporting "
          + "join fetch s.owner "
          + "join fetch s.tenant "
          + "left join fetch s.recipientUsers "
          + "where s.enabled = true")
  List<ReportingSchedule> findAllEnabledForScheduling();
}
