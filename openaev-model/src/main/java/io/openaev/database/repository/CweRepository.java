package io.openaev.database.repository;

import io.openaev.database.model.Cwe;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CweRepository extends CrudRepository<Cwe, String>, JpaSpecificationExecutor<Cwe> {

  Optional<Cwe> findByExternalId(@NotBlank String externalId);
}
