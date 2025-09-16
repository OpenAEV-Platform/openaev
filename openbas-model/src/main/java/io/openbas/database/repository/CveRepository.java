package io.openbas.database.repository;

import io.openbas.database.model.Cve;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CveRepository extends CrudRepository<Cve, String>, JpaSpecificationExecutor<Cve> {

  Optional<Cve> findByExternalId(String externalId);

  Set<Cve> getAllByIdInIgnoreCase(Set<String> ids);

  Set<Cve> getAllByExternalIdInIgnoreCase(Set<String> externalIds);
}
