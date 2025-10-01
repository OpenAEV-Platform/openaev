package io.openaev.database.repository;

import io.openaev.database.model.KillChainPhase;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KillChainPhaseRepository
    extends CrudRepository<KillChainPhase, String>, JpaSpecificationExecutor<KillChainPhase> {

  List<KillChainPhase> findAllByExternalIdInIgnoreCase(List<String> externalIds);

  @NotNull
  Optional<KillChainPhase> findById(@NotNull String id);

  @Query("SELECT k FROM KillChainPhase k WHERE k.shortName IN (:names)")
  List<KillChainPhase> findAllByShortName(@NotNull List<String> names);

  Optional<KillChainPhase> findByKillChainNameAndShortName(
      @NotNull String killChainName, @NotNull String shortName);
}
