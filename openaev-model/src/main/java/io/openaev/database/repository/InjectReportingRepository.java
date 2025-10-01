package io.openaev.database.repository;

import io.openaev.database.model.InjectStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectReportingRepository extends CrudRepository<InjectStatus, String> {

  @NotNull
  Optional<InjectStatus> findById(@NotNull String id);
}
