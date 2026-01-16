package io.openaev.database.repository;

import io.openaev.database.model.Domain;
import io.openaev.database.raw.RawDomain;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
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

  @Query(
      value =
          "SELECT d.domain_id, d.domain_name, d.domain_color, "
              + "d.domain_created_at, d.domain_updated_at "
              + "FROM domains d "
              + "WHERE d.domain_updated_at > :from ORDER BY d.domain_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawDomain> findForIndexing(@Param("from") Instant from);
}
