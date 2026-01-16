package io.openaev.database.repository;

import io.openaev.database.model.Domain;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DomainRepository
    extends CrudRepository<Domain, String>, JpaSpecificationExecutor<Domain> {

  @NotNull
  @Transactional(readOnly = true)
  Optional<Domain> findByName(@NotNull String name);

  @NotNull
  @Transactional(readOnly = true)
  List<Domain> findByNameIn(Collection<String> names);
}
