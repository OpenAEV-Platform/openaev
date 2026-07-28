package io.openaev.database.repository;

import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportingRepository
    extends CrudRepository<Reporting, String>, JpaSpecificationExecutor<Reporting> {

  Optional<Reporting> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<Reporting> findAllByContextTypeAndContextIdOrderByUpdatedAtDesc(
      @NotNull ReportingContextType contextType, @NotNull String contextId);

  List<Reporting> findAllByContextTypeAndContextIdIsNullOrderByUpdatedAtDesc(
      @NotNull ReportingContextType contextType);
}
