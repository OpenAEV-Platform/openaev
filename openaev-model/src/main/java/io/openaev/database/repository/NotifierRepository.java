package io.openaev.database.repository;

import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotifierRepository
    extends CrudRepository<Notifier, String>, JpaSpecificationExecutor<Notifier> {

  Optional<Notifier> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  boolean existsByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<Notifier> findAllByTenantId(@NotNull String tenantId);

  List<Notifier> findAllByTenantIdAndBuiltInTrue(@NotNull String tenantId);

  Optional<Notifier> findFirstByTenantIdAndTypeAndBuiltInTrue(
      @NotNull String tenantId, @NotNull NotifierType type);
}
