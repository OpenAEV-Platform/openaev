package io.openaev.database.repository;

import io.openaev.database.model.AssetAgentJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  @Query(
      value =
          """
    SELECT j FROM AssetAgentJob j WHERE j.agent.id = :agentId AND j.tenant.id = :tenantId AND j.inject IS NULL
    """)
  Optional<AssetAgentJob> findUpgradeJobByAgentIdAndInjectNull(
      @Param("agentId") String agentId, @Param("tenantId") String tenantId);

  @Query(
      value =
          "SELECT j.agent.id, COUNT(j) FROM AssetAgentJob j"
              + " WHERE j.agent.id IN :agentIds AND j.inject IS NOT NULL"
              + " GROUP BY j.agent.id")
  Set<Object[]> countPendingJobsByAgentIds(@Param("agentIds") Set<String> agentIds);

  @Modifying
  @Query(
      value = "DELETE FROM asset_agent_jobs "
              + "WHERE asset_agent_inject IN :injectIds "
              + "AND tenant_id = :tenantId",
      nativeQuery = true)
  void deleteAllByInjectIdsAndTenantId(
      @Param("injectIds") List<String> injectIds, @Param("tenantId") String tenantId);}
