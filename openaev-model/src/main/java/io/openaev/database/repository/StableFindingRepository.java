package io.openaev.database.repository;

import io.openaev.database.model.StableFinding;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StableFindingRepository
    extends JpaRepository<StableFinding, String>, JpaSpecificationExecutor<StableFinding> {

  Optional<StableFinding> findByKeyAndTenantId(String key, String tenantId);

  Optional<StableFinding> findByIdAndTenantIdIn(String id, Collection<String> tenantIds);

  @Override
  @EntityGraph(attributePaths = "sourceInjector")
  Page<StableFinding> findAll(Specification<StableFinding> specification, Pageable pageable);
}
