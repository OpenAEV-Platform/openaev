package io.openaev.database.repository;

import io.openaev.database.model.CollectorType;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectorTypeRepository extends CrudRepository<CollectorType, String> {

  Optional<CollectorType> findByName(@NotNull String name);
}
