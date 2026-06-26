package io.openaev.database.repository;

import io.openaev.database.model.AiTarget;
import io.openaev.database.model.AssetType;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTargetRepository
    extends CrudRepository<AiTarget, String>, JpaSpecificationExecutor<AiTarget> {

  Optional<AiTarget> findByExternalReference(@Param("externalReference") String externalReference);

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.AI_TARGET_TYPE
          + "' AND "
          + "(:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<AiTarget> findAllByName(String name);

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.AI_TARGET_TYPE
          + "' AND "
          + "a.id IN :ids")
  List<AiTarget> findAllByIds(@NotEmpty @Param("ids") Set<String> ids);
}
