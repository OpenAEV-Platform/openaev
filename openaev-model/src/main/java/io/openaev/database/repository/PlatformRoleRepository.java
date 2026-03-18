package io.openaev.database.repository;

import io.openaev.database.model.PlatformRole;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformRoleRepository
    extends CrudRepository<PlatformRole, String>, JpaSpecificationExecutor<PlatformRole> {

  long countByIdIn(Set<String> ids);
}
