package io.openaev.database.repository;

import io.openaev.database.model.Communication;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunicationRepository
    extends CrudRepository<Communication, String>, JpaSpecificationExecutor<Communication> {

  @NotNull
  Optional<Communication> findById(@NotNull String id);

  List<Communication> findByInjectId(@NotNull String injectId);

  boolean existsByIdentifierAndInjectTenantId(String identifier, String tenantId);
}
