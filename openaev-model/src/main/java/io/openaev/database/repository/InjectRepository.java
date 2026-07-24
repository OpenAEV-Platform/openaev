package io.openaev.database.repository;

import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;

import io.openaev.database.model.Inject;
import io.openaev.database.raw.RawExerciseInjectSummary;
import io.openaev.database.raw.RawInject;
import io.openaev.database.raw.RawInjectIndexing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for {@link Inject} entities.
 *
 * <p>This repository provides comprehensive data access operations for injects, which represent
 * individual attack simulation steps within exercises and scenarios. It supports:
 *
 * <ul>
 *   <li>Standard CRUD operations via {@link JpaRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Statistical queries via {@link StatisticRepository}
 *   <li>Complex queries for inject retrieval with relationships
 *   <li>Search engine indexing support
 *   <li>Import/export operations
 *   <li>Team and asset management operations
 * </ul>
 *
 * @see Inject
 * @see io.openaev.database.model.Exercise
 * @see io.openaev.database.model.Scenario
 */
@Repository
public interface InjectRepository
    extends JpaRepository<Inject, String>, JpaSpecificationExecutor<Inject>, StatisticRepository {

  @NotNull
  Optional<Inject> findById(@NotNull String id);

  @NotNull
  Optional<Inject> findWithStatusById(@NotNull String id);

  Optional<Inject> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  /**
   * Updates only an inject's {@code updated_at} timestamp, through Hibernate so the tenant
   * statement inspector covers it (a previous raw-JDBC helper bypassed it). Returns the number of
   * rows updated.
   */
  @Modifying
  @Query("UPDATE Inject i SET i.updatedAt = :updatedAt WHERE i.id = :id")
  @Transactional
  int updateUpdatedAt(@Param("id") @NotNull String id, @Param("updatedAt") Instant updatedAt);

  // -- SIMULATION --

  List<Inject> findByExerciseId(@NotNull String exerciseId);

  Optional<Inject> findByIdAndExerciseId(@NotNull String id, @NotNull String exerciseId);

  boolean existsByIdAndExerciseId(@NotNull String id, @NotNull String exerciseId);

  // -- SCENARIO --

  Optional<Inject> findByIdAndScenarioId(@NotNull String id, @NotNull String scenarioId);

  boolean existsByIdAndScenarioId(@NotNull String id, @NotNull String scenarioId);

  Set<Inject> findByScenarioId(@NotNull String scenarioId);

  // -- INDEXING --

  @Query(
      value =
          """
    WITH changed_injects AS (
        SELECT i2.inject_id FROM injects i2 WHERE i2.inject_updated_at > :from
        UNION
        SELECT i2.inject_id FROM injects i2
          JOIN injectors_contracts c2 ON c2.injector_contract_id = i2.inject_injector_contract
                                     AND c2.tenant_id = i2.tenant_id
          WHERE c2.injector_contract_updated_at > :from
        UNION
        SELECT d2.inject_parent_id FROM injects_dependencies d2
          WHERE d2.dependency_updated_at > :from
        UNION
        SELECT d2.inject_parent_id FROM injects_dependencies d2
          JOIN injects child2 ON child2.inject_id = d2.inject_children_id
          JOIN injectors_contracts c2 ON c2.injector_contract_id = child2.inject_injector_contract
                                     AND c2.tenant_id = child2.tenant_id
          WHERE c2.injector_contract_updated_at > :from
    ),
    ranked_injects AS (
        -- sort_ts is the indexing sort key AND the cursor value (InjectHandler copies it to
        -- EsInject.base_updated_at, which EsIndexingUtils.computeNewCursor advances on). It must
        -- therefore cover EVERY trigger of changed_injects: the inject itself, its contract, and
        -- its dependency rows / child contracts. A trigger missing from the key would give the row
        -- a sort_ts <= :from: the batch order could put stale keys last, letting the cursor stall
        -- or regress and freeze inject indexing (0 injects after the full-reindex migration).
        -- Filtering on sort_ts > :from then guarantees strict cursor progress (mirrors
        -- InjectExpectationRepository.ranked_expectations).
        SELECT r.inject_id, r.sort_ts
        FROM (
            SELECT ci.inject_id,
                   GREATEST(
                     f.inject_updated_at,
                     COALESCE(ic.injector_contract_updated_at, f.inject_updated_at),
                     COALESCE(dep.dependency_ts, f.inject_updated_at)
                   ) AS sort_ts
            FROM changed_injects ci
            JOIN injects f ON f.inject_id = ci.inject_id
            LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = f.inject_injector_contract
                                            AND ic.tenant_id = f.tenant_id
            LEFT JOIN LATERAL (
                SELECT MAX(GREATEST(d.dependency_updated_at,
                                    COALESCE(cc.injector_contract_updated_at, d.dependency_updated_at))) AS dependency_ts
                FROM injects_dependencies d
                JOIN injects child ON child.inject_id = d.inject_children_id
                LEFT JOIN injectors_contracts cc ON cc.injector_contract_id = child.inject_injector_contract
                                                AND cc.tenant_id = child.tenant_id
                WHERE d.inject_parent_id = f.inject_id
            ) dep ON TRUE
        ) r
        WHERE r.sort_ts > :from
        ORDER BY r.sort_ts ASC
        LIMIT :limit
    )
    SELECT f.inject_id, f.inject_title, f.inject_scenario, f.inject_exercise,
      f.inject_created_at, f.inject_updated_at, f.tenant_id, f.inject_injector_contract,
      ri.sort_ts AS inject_sort_ts,
      ic.injector_contract_updated_at, ins.tracking_sent_date,
      ic.injector_contract_platforms as inject_platforms,
      (SELECT array_agg(icap.attack_pattern_id)
       FROM injectors_contracts_attack_patterns icap
       WHERE icap.injector_contract_id = ic.injector_contract_id) as inject_attack_patterns,
      (SELECT array_agg(ap.phase_id)
       FROM injectors_contracts_attack_patterns icap
       JOIN attack_patterns_kill_chain_phases ap ON ap.attack_pattern_id = icap.attack_pattern_id
       WHERE icap.injector_contract_id = ic.injector_contract_id) as inject_kill_chain_phases,
      (SELECT array_agg(idp.inject_children_id)
       FROM injects_dependencies idp
       WHERE idp.inject_parent_id = f.inject_id) as inject_children,
      (SELECT array_agg(idp.inject_children_id)
       FROM injects_dependencies idp
       WHERE idp.inject_parent_id = f.inject_id) as attack_pattern_children,
      (SELECT array_agg(icap_c.attack_pattern_id)
       FROM injects_dependencies idp
       JOIN injects child ON child.inject_id = idp.inject_children_id
       JOIN injectors_contracts ic_c ON ic_c.injector_contract_id = child.inject_injector_contract
                                    AND ic_c.tenant_id = child.tenant_id
       JOIN injectors_contracts_attack_patterns icap_c ON icap_c.injector_contract_id = ic_c.injector_contract_id
       WHERE idp.inject_parent_id = f.inject_id) as attack_patterns_children,
      ins.status_name as inject_status_name,
      (SELECT array_agg(it.tag_id)
       FROM injects_tags it WHERE it.inject_id = f.inject_id) as inject_tags,
      (SELECT array_agg(ia.asset_id)
       FROM injects_assets ia WHERE ia.inject_id = f.inject_id) as inject_assets,
      (SELECT array_agg(iag.asset_group_id)
       FROM injects_asset_groups iag WHERE iag.inject_id = f.inject_id) as inject_asset_groups,
      (SELECT array_agg(x.team_id) FROM (
        SELECT ite.team_id FROM injects_teams ite WHERE ite.inject_id = f.inject_id
        UNION ALL
        SELECT et.team_id FROM exercises_teams et WHERE et.exercise_id = f.inject_exercise AND f.inject_all_teams
        UNION ALL
        SELECT st.team_id FROM scenarios_teams st WHERE st.scenario_id = f.inject_scenario AND f.inject_all_teams
      ) x) as inject_teams
    FROM injects f
    JOIN ranked_injects ri ON ri.inject_id = f.inject_id
    LEFT JOIN injects_statuses ins ON ins.status_inject = f.inject_id
    LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = f.inject_injector_contract
                                    AND ic.tenant_id = f.tenant_id
    ORDER BY ri.sort_ts ASC
    """,
      nativeQuery = true)
  List<RawInjectIndexing> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);

  @Query(
      value =
          "select i.*, i.tenant_id as tenantId from injects i where i.inject_injector_contract = '49229430-b5b5-431f-ba5b-f36f599b0233'"
              + " and i.inject_content like :challengeId"
              + " and i.tenant_id = :#{#tenantContext.currentTenant}",
      nativeQuery = true)
  List<Inject> findAllForChallengeId(@Param("challengeId") String challengeId);

  @Query(
      value =
          "select i from Inject i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.exercise.id = :exerciseId")
  List<Inject> findAllForExerciseAndDoc(
      @Param("exerciseId") String exerciseId, @Param("documentId") String documentId);

  @Query(
      value =
          "select i from Inject i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.scenario.id = :scenarioId")
  List<Inject> findAllForScenarioAndDoc(
      @Param("scenarioId") String scenarioId, @Param("documentId") String documentId);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, inject_exercise, "
              + "inject_depends_duration, inject_content, tenant_id) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :exercise, :dependsDuration, :content, :#{#tenantContext.currentTenant})",
      nativeQuery = true)
  void importSaveForExercise(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("exercise") String exerciseId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, inject_scenario, "
              + "inject_depends_duration, inject_content, tenant_id) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :scenario, :dependsDuration, :content, :#{#tenantContext.currentTenant})",
      nativeQuery = true)
  void importSaveForScenario(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("scenario") String scenarioId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, "
              + "inject_depends_duration, inject_content, tenant_id) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :dependsDuration, :content, :#{#tenantContext.currentTenant})",
      nativeQuery = true)
  void importSaveStandAlone(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value = "insert into injects_tags (inject_id, tag_id) values (:injectId, :tagId)",
      nativeQuery = true)
  void addTag(@Param("injectId") String injectId, @Param("tagId") String tagId);

  @Modifying
  @Query(
      value = "insert into injects_teams (inject_id, team_id) values (:injectId, :teamId)",
      nativeQuery = true)
  void addTeam(@Param("injectId") String injectId, @Param("teamId") String teamId);

  @Override
  @Query(
      "select count(distinct i) from Inject i "
          + "join i.exercise as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct i) from Inject i where i.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "WITH inject_teams AS ( "
              + "    SELECT inject_id, array_agg(team_id) as team_ids "
              + "    FROM injects_teams "
              + "    WHERE inject_id IN (:ids) "
              + "    GROUP BY inject_id "
              + "), "
              + "inject_assets AS ( "
              + "    SELECT  "
              + "        i.inject_id,  "
              + "        array_agg(DISTINCT a.asset_id) as asset_ids "
              + "    FROM injects i "
              + "    LEFT JOIN injects_assets ia ON i.inject_id = ia.inject_id "
              + "    LEFT JOIN injects_asset_groups iag ON i.inject_id = iag.inject_id "
              + "    LEFT JOIN asset_groups_assets aga ON aga.asset_group_id = iag.asset_group_id "
              + "    LEFT JOIN assets a ON a.asset_id = ia.asset_id OR aga.asset_id = a.asset_id "
              + "    WHERE i.inject_id IN (:ids) "
              + "    GROUP BY i.inject_id "
              + "), "
              + "inject_asset_groups AS ( "
              + "    SELECT inject_id, array_agg(asset_group_id) as asset_group_ids "
              + "    FROM injects_asset_groups "
              + "    WHERE inject_id IN (:ids) "
              + "    GROUP BY inject_id "
              + "), "
              + "inject_expectations AS ( "
              + "    SELECT inject_id, array_agg(inject_expectation_id) as expectation_ids "
              + "    FROM injects_expectations "
              + "    WHERE inject_id IN (:ids) "
              + "    GROUP BY inject_id "
              + "), "
              + "inject_communications AS ( "
              + "    SELECT communication_inject as inject_id, array_agg(communication_id) as communication_ids "
              + "    FROM communications "
              + "    WHERE communication_inject IN (:ids) "
              + "    GROUP BY communication_inject "
              + "), "
              + "inject_kill_chains AS ( "
              + "    SELECT  "
              + "        i.inject_id, "
              + "        array_agg(DISTINCT apkcp.phase_id) as phase_ids "
              + "    FROM injects i "
              + "    JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract "
              + "    JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = icap.attack_pattern_id "
              + "    WHERE i.inject_id IN (:ids) "
              + "    GROUP BY i.inject_id "
              + "), "
              + "inject_platforms AS ( "
              + "    SELECT  "
              + "        i.inject_id, "
              + "        array_union_agg(injcon.injector_contract_platforms) as platform_ids "
              + "    FROM injects i "
              + "    JOIN injectors_contracts injcon ON injcon.injector_contract_id = i.inject_injector_contract "
              + "    WHERE i.inject_id IN (:ids) "
              + "    GROUP BY i.inject_id "
              + ") "
              + "SELECT  "
              + "    i.inject_id, "
              + "    ins.status_name, "
              + "    i.inject_scenario, "
              + "    COALESCE(it.team_ids, '{}') as inject_teams, "
              + "    COALESCE(ia.asset_ids, '{}') as inject_assets, "
              + "    COALESCE(iag.asset_group_ids, '{}') as inject_asset_groups, "
              + "    COALESCE(ie.expectation_ids, '{}') as inject_expectations, "
              + "    COALESCE(ic.communication_ids, '{}') as inject_communications, "
              + "    COALESCE(ikc.phase_ids, '{}') as inject_kill_chain_phases, "
              + "    COALESCE(ip.platform_ids, '{}') as inject_platforms "
              + "FROM injects i "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = i.inject_id "
              + "LEFT JOIN inject_teams it ON it.inject_id = i.inject_id "
              + "LEFT JOIN inject_assets ia ON ia.inject_id = i.inject_id "
              + "LEFT JOIN inject_asset_groups iag ON iag.inject_id = i.inject_id "
              + "LEFT JOIN inject_expectations ie ON ie.inject_id = i.inject_id "
              + "LEFT JOIN inject_communications ic ON ic.inject_id = i.inject_id "
              + "LEFT JOIN inject_kill_chains ikc ON ikc.inject_id = i.inject_id "
              + "LEFT JOIN inject_platforms ip ON ip.inject_id = i.inject_id "
              + "WHERE i.inject_id IN (:ids);",
      nativeQuery = true)
  List<RawInject> findRawByIds(@Param("ids") List<String> ids);

  /**
   * Returns aggregated inject metadata for an exercise in a single query: distinct platforms, total
   * communications count, and distinct kill-chain phase IDs. Avoids loading all individual inject
   * rows for exercises with thousands of injects.
   */
  @Query(
      value =
          "SELECT "
              + "  COALESCE((SELECT array_agg(DISTINCT x) FROM ("
              + "    SELECT unnest(injcon.injector_contract_platforms) AS x "
              + "    FROM injects i "
              + "    JOIN injectors_contracts injcon ON injcon.injector_contract_id = i.inject_injector_contract "
              + "    WHERE i.inject_exercise = :exerciseId"
              + "  ) sub), '{}') AS platforms, "
              + "  (SELECT count(*) FROM communications "
              + "    WHERE communication_inject IN ("
              + "      SELECT inject_id FROM injects WHERE inject_exercise = :exerciseId"
              + "  )) AS communications_number, "
              + "  COALESCE((SELECT array_agg(DISTINCT apkcp.phase_id) "
              + "    FROM injects i "
              + "    JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract "
              + "    JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = icap.attack_pattern_id "
              + "    WHERE i.inject_exercise = :exerciseId"
              + "  ), '{}') AS kill_chain_phase_ids",
      nativeQuery = true)
  RawExerciseInjectSummary findInjectSummaryByExerciseId(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          " SELECT injects.inject_id, "
              + "coalesce(array_agg(it.team_id) FILTER ( WHERE it.team_id IS NOT NULL ), '{}') as inject_teams "
              + "FROM injects "
              + "LEFT JOIN injects_teams it ON injects.inject_id = it.inject_id "
              + "WHERE injects.inject_id IN :ids AND it.team_id = :teamId "
              + "GROUP BY injects.inject_id",
      nativeQuery = true)
  Set<RawInject> findRawInjectTeams(
      @Param("ids") Collection<String> ids, @Param("teamId") String teamId);

  // -- TEAM --

  /**
   * Bumps all injects of an exercise so the incremental search-engine indexer refreshes their
   * denormalized team linkage (inject_teams, all-teams derivation, expectation team sides).
   */
  @Modifying
  @Query(
      value = "UPDATE injects SET inject_updated_at = now() WHERE inject_exercise = :exerciseId",
      nativeQuery = true)
  @Transactional
  void touchUpdatedAtByExerciseId(@Param("exerciseId") String exerciseId);

  /** Same as {@link #touchUpdatedAtByExerciseId(String)} for scenario injects. */
  @Modifying
  @Query(
      value = "UPDATE injects SET inject_updated_at = now() WHERE inject_scenario = :scenarioId",
      nativeQuery = true)
  @Transactional
  void touchUpdatedAtByScenarioId(@Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM injects i WHERE it.inject_id = i.inject_id AND i.inject_exercise = :exerciseId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForExercise(
      @Param("exerciseId") final String exerciseId, @Param("teamIds") final List<String> teamIds);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM injects i WHERE it.inject_id = i.inject_id AND i.inject_scenario = :scenarioId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForScenario(
      @Param("scenarioId") final String scenarioId, @Param("teamIds") final List<String> teamIds);

  @Query(
      value =
          """
    SELECT DISTINCT i.inject_id AS id, i.inject_title AS label, i.inject_created_at
    FROM injects i
    INNER JOIN findings f ON f.finding_inject_id = i.inject_id
    WHERE (:title IS NULL OR LOWER(i.inject_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      AND i.tenant_id = :#{#tenantContext.currentTenant}
      ORDER BY i.inject_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindings(@Param("title") String title, Pageable pageable);

  @Query(
      value =
          """
    SELECT DISTINCT i.inject_id AS id, i.inject_title AS label, i.inject_created_at
    FROM injects i
    INNER JOIN findings f ON f.finding_inject_id = i.inject_id
    LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
    LEFT JOIN scenarios_exercises se ON se.exercise_id = i.inject_exercise
    WHERE (i.inject_exercise = :sourceId OR se.scenario_id = :sourceId OR fa.asset_id = :sourceId)
      AND (:title IS NULL OR LOWER(i.inject_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      AND i.tenant_id = :#{#tenantContext.currentTenant}
      ORDER BY i.inject_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("title") String title, Pageable pageable);

  @Query(
      value = "SELECT i.inject_content FROM injects i WHERE i.inject_id IN :injectIds",
      nativeQuery = true)
  List<String> findContentsByInjectIds(@NotBlank Set<String> injectIds);

  /**
   * Check if an Inject exists by its ID without loading the entity. This is useful for because of
   * the cascade configuration
   *
   * @param id the ID of the Inject to check
   * @return true if the Inject exists, false otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inject i WHERE i.id = :id")
  boolean existsByIdWithoutLoading(@Param("id") String id);

  /**
   * Check if an Inject exists by its ID, where the Inject is an atomic testing.
   *
   * @param id ID of the inject to check
   * @return true if the Inject exists and is an atomic testing, false otherwise
   */
  boolean existsByIdAndScenarioIsNullAndExerciseIsNull(String id);

  @Query(
      value =
          "SELECT i.inject_id FROM injects i "
              + "JOIN injectors_contracts ic ON i.inject_injector_contract = ic.injector_contract_id "
              + "JOIN payloads p ON ic.injector_contract_payload = p.payload_id "
              + "WHERE p.payload_type = '"
              + DNS_RESOLUTION_TYPE
              + "' "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  List<String> findInjectIdsWithDnsResolutionContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, payloads p "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_payload = p.payload_id "
              + "AND p.payload_type = '"
              + DNS_RESOLUTION_TYPE
              + "' "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithDnsResolutionContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Query(
      value =
          "SELECT i.inject_id FROM injects i "
              + "JOIN injectors_contracts ic ON i.inject_injector_contract = ic.injector_contract_id "
              + "JOIN payloads p ON ic.injector_contract_payload = p.payload_id "
              + "WHERE p.payload_type = '"
              + FILE_DROP_TYPE
              + "' "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  List<String> findInjectIdsWithFileDropContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, payloads p "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_payload = p.payload_id "
              + "AND p.payload_type = '"
              + FILE_DROP_TYPE
              + "' "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithFileDropContractsByScenarioId(@Param("scenarioId") String scenarioId);

  @Query(
      value =
          "SELECT DISTINCT i.inject_id FROM injects i "
              + "JOIN injectors_contracts ic ON i.inject_injector_contract = ic.injector_contract_id "
              + "JOIN injectors_contracts_vulnerabilities icv ON ic.injector_contract_id = icv.injector_contract_id "
              + "WHERE i.inject_scenario = :scenarioId",
      nativeQuery = true)
  List<String> findInjectIdsWithVulnerableContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, injectors_contracts_vulnerabilities icv "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icv.injector_contract_id "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithVulnerableContractsByScenarioId(@Param("scenarioId") String scenarioId);

  @Query(
      value =
          "SELECT DISTINCT i.inject_id FROM injects i "
              + "JOIN injectors_contracts ic ON i.inject_injector_contract = ic.injector_contract_id "
              + "JOIN injectors_contracts_attack_patterns icap ON ic.injector_contract_id = icap.injector_contract_id "
              + "WHERE i.inject_scenario = :scenarioId",
      nativeQuery = true)
  List<String> findInjectIdsWithAttackPatternContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, injectors_contracts_attack_patterns icap "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icap.injector_contract_id "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithAttackPatternContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Query(
      value =
          "SELECT i.inject_id FROM injects i WHERE i.inject_injector_contract = :injectorContract AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  List<String> findInjectIdsByScenarioIdAndInjectorContract(
      @Param("injectorContract") String injectorContract, @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i WHERE i.inject_injector_contract = :injectorContract AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllByScenarioIdAndInjectorContract(String injectorContract, String scenarioId);

  @EntityGraph(attributePaths = {"expectations", "injectorContract"})
  @Query("SELECT i FROM Inject i WHERE i.id IN :ids")
  List<Inject> findAllByIdWithExpectations(@Param("ids") List<String> ids);

  @Modifying
  @Query(value = "DELETE FROM injects WHERE inject_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);

  @Modifying
  @Query(value = "DELETE FROM injects WHERE inject_id IN :ids", nativeQuery = true)
  void deleteByAllIdsNative(@Param("ids") List<String> ids);

  @Query("SELECT i FROM Inject i WHERE i.exercise.id = :simulationId")
  List<Inject> findAllInjectBySimulationId(@Param("simulationId") String simulationId);
}
