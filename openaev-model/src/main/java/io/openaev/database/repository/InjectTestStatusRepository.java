package io.openaev.database.repository;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTestStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

public interface InjectTestStatusRepository
    extends CrudRepository<InjectTestStatus, String>, JpaSpecificationExecutor<InjectTestStatus> {

  @NotNull
  Optional<InjectTestStatus> findById(@NotNull String id);

  Optional<InjectTestStatus> findByInject(@NotNull Inject inject);
}
