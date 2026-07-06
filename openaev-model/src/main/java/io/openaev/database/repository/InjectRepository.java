package io.openaev.database.repository;

import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;

import io.openaev.database.model.Inject;
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

  /**
   * Retrieves injects modified since {@code from} for incremental search-engine indexing.
   *
   * <p>Performance design: each one-to-many relationship (attack patterns, children, tags, assets,
   * teams…) is pre-aggregated in its own CTE scoped to the eligible-inject set. The final SELECT
   * therefore performs only 1:1 LEFT JOINs with no GROUP BY, eliminating the Cartesian-product row
   * explosion that the previous flat-join + GROUP BY approach produced (e.g. 5 attack_patterns × 3
   * tags × 4 teams = 60 rows per inject before aggregation).
   *
   * <p>The eligible-inject filter uses a UNION of independently index-backed branches (same
   * strategy as before) so each branch can use its own index.
   */
  @Query(
      value =
          // -- 1. Eligible inject IDs (UNION deduplicates automatically) --
          "WITH eligible AS ( "
              + "    SELECT i2.inject_id FROM injects i2 WHERE i2.inject_updated_at > :from "
              + "    UNION "
              + "    SELECT i2.inject_id FROM injects i2 "
              + "        JOIN injectors_contracts c2 ON c2.injector_contract_id = i2.inject_injector_contract "
              + "        WHERE c2.injector_contract_updated_at > :from "
              + "    UNION "
              + "    SELECT d2.inject_parent_id FROM injects_dependencies d2 "
              + "        WHERE d2.dependency_updated_at > :from "
              + "    UNION "
              + "    SELECT d2.inject_parent_id FROM injects_dependencies d2 "
              + "        JOIN injects child2 ON child2.inject_id = d2.inject_children_id "
              + "        JOIN injectors_contracts c2 ON c2.injector_contract_id = child2.inject_injector_contract "
              + "        WHERE c2.injector_contract_updated_at > :from "
              + "), "
              // -- 2. Attack patterns + kill-chain phases per inject (via its contract) --
              + "agg_attack_patterns AS ( "
              + "    SELECT i.inject_id, "
              + "           array_agg(DISTINCT icap.attack_pattern_id) "
              + "               FILTER (WHERE icap.attack_pattern_id IS NOT NULL) AS attack_pattern_ids, "
              + "           array_agg(DISTINCT ap.phase_id) "
              + "               FILTER (WHERE ap.phase_id IS NOT NULL)            AS phase_ids "
              + "    FROM eligible e "
              + "    JOIN injects i ON i.inject_id = e.inject_id "
              + "    LEFT JOIN injectors_contracts ic "
              + "           ON ic.injector_contract_id = i.inject_injector_contract "
              + "    LEFT JOIN injectors_contracts_attack_patterns icap "
              + "           ON icap.injector_contract_id = ic.injector_contract_id "
              + "    LEFT JOIN attack_patterns_kill_chain_phases ap "
              + "           ON ap.attack_pattern_id = icap.attack_pattern_id "
              + "    GROUP BY i.inject_id "
              + "), "
              // -- 3. Child injects + their attack patterns per parent inject --
              + "agg_children AS ( "
              + "    SELECT idp.inject_parent_id, "
              + "           array_agg(DISTINCT idp.inject_children_id) "
              + "               FILTER (WHERE idp.inject_children_id IS NOT NULL)  AS children_ids, "
              + "           array_agg(DISTINCT icap_c.attack_pattern_id) "
              + "               FILTER (WHERE icap_c.attack_pattern_id IS NOT NULL) AS children_ap_ids "
              + "    FROM eligible e "
              + "    JOIN injects_dependencies idp ON idp.inject_parent_id = e.inject_id "
              + "    LEFT JOIN injects child_i ON child_i.inject_id = idp.inject_children_id "
              + "    LEFT JOIN injectors_contracts_attack_patterns icap_c "
              + "           ON icap_c.injector_contract_id = child_i.inject_injector_contract "
              + "    GROUP BY idp.inject_parent_id "
              + "), "
              // -- 4. Tags per inject --
              + "agg_tags AS ( "
              + "    SELECT it.inject_id, "
              + "           array_agg(it.tag_id) FILTER (WHERE it.tag_id IS NOT NULL) AS tag_ids "
              + "    FROM eligible e "
              + "    JOIN injects_tags it ON it.inject_id = e.inject_id "
              + "    GROUP BY it.inject_id "
              + "), "
              // -- 5. Assets per inject --
              + "agg_assets AS ( "
              + "    SELECT ia.inject_id, "
              + "           array_agg(ia.asset_id) FILTER (WHERE ia.asset_id IS NOT NULL) AS asset_ids "
              + "    FROM eligible e "
              + "    JOIN injects_assets ia ON ia.inject_id = e.inject_id "
              + "    GROUP BY ia.inject_id "
              + "), "
              // -- 6. Asset groups per inject --
              + "agg_asset_groups AS ( "
              + "    SELECT iag.inject_id, "
              + "           array_agg(iag.asset_group_id) "
              + "               FILTER (WHERE iag.asset_group_id IS NOT NULL) AS asset_group_ids "
              + "    FROM eligible e "
              + "    JOIN injects_asset_groups iag ON iag.inject_id = e.inject_id "
              + "    GROUP BY iag.inject_id "
              + "), "
              // -- 7. Teams per inject: direct + exercise all-teams + scenario all-teams --
              // Deduplication is handled in Java by Set<String> in
              // RawInjectIndexing.getInject_teams()
              + "agg_teams AS ( "
              + "    SELECT all_t.inject_id, array_agg(all_t.team_id) AS team_ids "
              + "    FROM ( "
              + "        SELECT ite.inject_id, ite.team_id "
              + "        FROM eligible e "
              + "        JOIN injects_teams ite ON ite.inject_id = e.inject_id "
              + "        WHERE ite.team_id IS NOT NULL "
              + "        UNION ALL "
              + "        SELECT f.inject_id, et.team_id "
              + "        FROM eligible e "
              + "        JOIN injects f ON f.inject_id = e.inject_id AND f.inject_all_teams "
              + "        JOIN exercises_teams et ON et.exercise_id = f.inject_exercise "
              + "        WHERE et.team_id IS NOT NULL "
              + "        UNION ALL "
              + "        SELECT f.inject_id, st.team_id "
              + "        FROM eligible e "
              + "        JOIN injects f ON f.inject_id = e.inject_id AND f.inject_all_teams "
              + "        JOIN scenarios_teams st ON st.scenario_id = f.inject_scenario "
              + "        WHERE st.team_id IS NOT NULL "
              + "    ) all_t "
              + "    GROUP BY all_t.inject_id "
              + ") "
              // -- 8. Final SELECT: 1:1 JOINs only — no GROUP BY, no row explosion --
              + "SELECT "
              + "    f.inject_id, f.inject_title, f.inject_scenario, f.inject_exercise, "
              + "    f.inject_created_at, f.inject_updated_at, f.tenant_id, f.inject_injector_contract, "
              + "    ic.injector_contract_updated_at, ins.tracking_sent_date, "
              + "    ic.injector_contract_platforms    AS inject_platforms, "
              + "    aap.attack_pattern_ids            AS inject_attack_patterns, "
              + "    aap.phase_ids                     AS inject_kill_chain_phases, "
              + "    ach.children_ids                  AS inject_children, "
              + "    ach.children_ap_ids               AS attack_patterns_children, "
              + "    ins.status_name                   AS inject_status_name, "
              + "    ata.tag_ids                       AS inject_tags, "
              + "    aas.asset_ids                     AS inject_assets, "
              + "    aag.asset_group_ids               AS inject_asset_groups, "
              + "    ate.team_ids                      AS inject_teams "
              + "FROM injects f "
              + "JOIN eligible e ON e.inject_id = f.inject_id "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = f.inject_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = f.inject_injector_contract "
              + "LEFT JOIN agg_attack_patterns aap ON aap.inject_id = f.inject_id "
              + "LEFT JOIN agg_children ach ON ach.inject_parent_id = f.inject_id "
              + "LEFT JOIN agg_tags ata ON ata.inject_id = f.inject_id "
              + "LEFT JOIN agg_assets aas ON aas.inject_id = f.inject_id "
              + "LEFT JOIN agg_asset_groups aag ON aag.inject_id = f.inject_id "
              + "LEFT JOIN agg_teams ate ON ate.inject_id = f.inject_id "
              + "ORDER BY GREATEST(f.inject_updated_at, ic.injector_contract_updated_at) ASC "
              + "LIMIT :limit;",
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
