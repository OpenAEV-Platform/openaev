package io.openaev.database.repository;

import io.openaev.database.model.ImportMapper;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportMapperRepository extends CrudRepository<ImportMapper, UUID> {

  @NotNull
  Page<ImportMapper> findAll(@NotNull Specification<ImportMapper> spec, @NotNull Pageable pageable);
}
