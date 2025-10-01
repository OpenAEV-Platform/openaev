package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectorRepository
    extends CrudRepository<Injector, String>, JpaSpecificationExecutor<Injector> {

  @NotNull
  Optional<Injector> findById(@NotNull String id);

  @NotNull
  Optional<Injector> findByType(@NotNull String type);

  List<Injector> findAllByPayloads(@NotNull Boolean payloads);
}
