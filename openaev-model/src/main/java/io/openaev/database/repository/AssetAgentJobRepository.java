package io.openaev.database.repository;

import io.openaev.database.model.AssetAgentJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAgentJobRepository
    extends CrudRepository<AssetAgentJob, String>, JpaSpecificationExecutor<AssetAgentJob> {

  @NotNull
  Optional<AssetAgentJob> findById(@NotNull String id);

  @Modifying
  @Query(
      value = "DELETE FROM asset_agent_jobs j WHERE j.asset_agent_id = :assetAgentJobId",
      nativeQuery = true)
  void deleteById(@Param("assetAgentJobId") @NotBlank String assetAgentJobId);
}
