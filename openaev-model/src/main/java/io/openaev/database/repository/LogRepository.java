package io.openaev.database.repository;

import io.openaev.database.model.Log;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogRepository extends CrudRepository<Log, String>, JpaSpecificationExecutor<Log> {

  @NotNull
  Optional<Log> findById(@NotNull String id);
}
