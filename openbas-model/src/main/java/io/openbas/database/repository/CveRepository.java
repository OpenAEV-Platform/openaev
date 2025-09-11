package io.openbas.database.repository;

import io.openbas.database.model.Cve;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CveRepository extends CrudRepository<Cve, String>, JpaSpecificationExecutor<Cve> {

  Optional<Cve> findByExternalId(String externalId);

  // @Query("SELECT v FROM Cve v WHERE LOWER(v.externalId) IN :externalIds")
  List<Cve> findAllByExternalIdInIgnoreCase(List<String> externalIds);
}
