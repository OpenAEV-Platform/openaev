package io.openaev.database.repository;

import io.openaev.database.model.KillChainPhase;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KillChainPhaseRepository
    extends CrudRepository<KillChainPhase, String>, JpaSpecificationExecutor<KillChainPhase> {

  @NotNull
  Optional<KillChainPhase> findById(@NotNull String id);

  List<KillChainPhase> findAllByExternalIdInIgnoreCaseAndTenantId(
      List<String> externalIds, @NotNull String tenantId);

  Optional<KillChainPhase> findByKillChainNameAndShortNameAndTenantId(
      @NotNull String killChainName, @NotNull String shortName, @NotNull String tenantId);

  Optional<KillChainPhase> findByStixIdAndTenantId(
      @NotNull String stixId, @NotNull String tenantId);

  @Query(
      "SELECT DISTINCT kcp FROM Inject i JOIN i.injectorContract ic"
          + " JOIN ic.attackPatterns ap JOIN ap.killChainPhases kcp"
          + " WHERE i.exercise.id = :exerciseId AND kcp.tenant.id = i.exercise.tenant.id")
  List<KillChainPhase> findDistinctByExerciseId(@Param("exerciseId") String exerciseId);

  @Query(
      "SELECT ap.id, kcp.id FROM AttackPattern ap JOIN ap.killChainPhases kcp"
          + " WHERE ap.id IN :attackPatternIds AND kcp.tenant.id = ap.tenant.id")
  List<Object[]> findPhaseIdsByAttackPatternIds(
      @Param("attackPatternIds") Collection<String> attackPatternIds);
}
