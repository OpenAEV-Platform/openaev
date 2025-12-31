package io.openaev.database.repository;

import io.openaev.database.model.Domain;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainRepository
    extends CrudRepository<Domain, String>, JpaSpecificationExecutor<Domain> {

  @NotNull
  Optional<Domain> findByName(@NotNull String name);
}
