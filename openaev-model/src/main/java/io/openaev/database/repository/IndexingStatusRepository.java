package io.openaev.database.repository;

import io.openaev.database.model.IndexingStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexingStatusRepository
    extends CrudRepository<IndexingStatus, String>, JpaSpecificationExecutor<IndexingStatus> {

  @NotNull
  Optional<IndexingStatus> findByType(@NotNull String type);
}
