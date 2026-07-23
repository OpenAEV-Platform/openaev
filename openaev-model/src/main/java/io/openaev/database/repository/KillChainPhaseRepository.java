package io.openaev.database.repository;

import io.openaev.database.model.KillChainPhase;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KillChainPhaseRepository
    extends CrudRepository<KillChainPhase, String>, JpaSpecificationExecutor<KillChainPhase> {

  List<KillChainPhase> findAllByExternalIdInIgnoreCase(List<String> externalIds);

  @NotNull
  Optional<KillChainPhase> findById(@NotNull String id);

  Optional<KillChainPhase> findByKillChainNameAndShortName(
      @NotNull String killChainName, @NotNull String shortName);

  // The database unique key is (phase_stix_id, tenant_id): upserts must match on the STIX id
  // first, otherwise a renamed phase (same STIX id, new short name) is treated as a new row and
  // violates the constraint. The Hibernate tenant filter scopes this to the current tenant.
  Optional<KillChainPhase> findByStixId(@NotNull String stixId);
}
