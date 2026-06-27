package io.openaev.database.repository;

import io.openaev.database.model.AiTarget;
import io.openaev.database.model.AssetType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTargetRepository
    extends CrudRepository<AiTarget, String>, JpaSpecificationExecutor<AiTarget> {

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.type = '"
          + AssetType.Values.AI_TARGET_TYPE
          + "' AND "
          + "(:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<AiTarget> findAllByName(String name);
}
