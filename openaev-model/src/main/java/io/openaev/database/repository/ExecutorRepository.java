package io.openaev.database.repository;

import io.openaev.database.model.Executor;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutorRepository extends CrudRepository<Executor, String> {

  @NotNull
  Optional<Executor> findById(@NotNull String id);

  @NotNull
  Optional<Executor> findByType(@NotNull String type);
}
