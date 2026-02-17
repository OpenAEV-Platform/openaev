package io.openaev.database.repository;

import io.openaev.database.model.DataPack;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataPackRepository
    extends CrudRepository<DataPack, String>, JpaSpecificationExecutor<DataPack> {

  @NotNull
  Optional<DataPack> findById(@NotNull String id);
}
