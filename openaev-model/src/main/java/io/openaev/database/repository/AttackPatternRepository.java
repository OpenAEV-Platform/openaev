package io.openaev.database.repository;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.raw.RawAttackPattern;
import io.openaev.utils.Constants;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackPatternRepository
    extends CrudRepository<AttackPattern, String>, JpaSpecificationExecutor<AttackPattern> {

  @NotNull
  Optional<AttackPattern> findById(@NotNull String id);

  List<AttackPattern> findAllByIdIn(List<String> externalIds);

  Optional<AttackPattern> findByExternalId(@NotNull String externalId);

  List<AttackPattern> findAllByExternalIdInIgnoreCase(List<String> externalIds);

  Optional<AttackPattern> findByStixId(@NotNull String stixId);

  @Query(
      value =
          "select ap.*, array_remove(array_agg(apphase.phase_id), NULL) as attack_pattern_kill_chain_phases from attack_patterns ap "
              + "left join attack_patterns_kill_chain_phases apphase ON ap.attack_pattern_id = apphase.attack_pattern_id GROUP BY ap.attack_pattern_id",
      nativeQuery = true)
  List<RawAttackPattern> rawAll();

  // -- INDEXING --

  @Query(
      value =
          "SELECT ap.attack_pattern_id, ap.attack_pattern_stix_id, ap.attack_pattern_name,"
              + " ap.attack_pattern_description, ap.attack_pattern_external_id, ap.attack_pattern_platforms, "
              + " ap.attack_pattern_created_at, ap.attack_pattern_updated_at, ap.attack_pattern_parent, apkcp.phase_id AS attack_pattern_kill_chain_phases "
              + "FROM attack_patterns ap "
              + "LEFT JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = ap.attack_pattern_id "
              + "WHERE ap.attack_pattern_updated_at > :from ORDER BY ap.attack_pattern_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawAttackPattern> findForIndexing(@Param("from") Instant from);
}
