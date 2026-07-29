package io.openaev.database.repository;

import io.openaev.database.model.ArticleInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.raw.RawGlobalScoreExpectation;
import io.openaev.database.raw.RawInjectExpectationIndexing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectExpectationRepository
    extends CrudRepository<BaseInjectExpectation, String>,
        JpaSpecificationExecutor<BaseInjectExpectation> {

  // JSON predicates over inject_expectation_results: a result "fills" the expectation when its
  // result text is non-empty. Keys are the Java property names serialized by JsonType (camelCase).
  // LATERAL is a noise word for a function-call FROM item in PostgreSQL (same semantics and plan),
  // but it matters for multi-tenancy v2: the fail-closed TenantStatementInspector only accepts
  // table functions carrying the LATERAL prefix (they unnest a column of the current row, never a
  // whole table). Without it, any query embedding these predicates that also touches a
  // tenant-active table (e.g. collectors) is refused with TENANT_FILTERING_REFUSED (#7007).
  String RESULTS_HAS_NO_RESULT_FOR_SOURCE =
      "NOT EXISTS (SELECT 1 FROM LATERAL jsonb_array_elements(e.inject_expectation_results::jsonb) r "
          + "WHERE r->>'sourceId' = :sourceId AND COALESCE(r->>'result', '') <> '') ";
  String RESULTS_HAS_NO_RESULT_AT_ALL =
      "NOT EXISTS (SELECT 1 FROM LATERAL jsonb_array_elements(e.inject_expectation_results::jsonb) r "
          + "WHERE COALESCE(r->>'result', '') <> '') ";

  @NotNull
  Optional<BaseInjectExpectation> findById(@NotNull String id);

  // -- COLLECTOR-POLLED "NOT FILLED" QUERIES --
  // These used to load the entire expectation table for a type and filter in Java; the
  // source/result filtering is now pushed into SQL and the result set is bounded.

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NOT NULL "
              + "AND "
              + RESULTS_HAS_NO_RESULT_FOR_SOURCE
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findAgentExpectationsNotFilledForSource(
      @Param("tenantId") String tenantId,
      @Param("type") String type,
      @Param("sourceId") String sourceId,
      @Param("limit") int limit);

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NOT NULL "
              + "AND "
              + RESULTS_HAS_NO_RESULT_AT_ALL
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findAgentExpectationsNotFilled(
      @Param("tenantId") String tenantId, @Param("type") String type, @Param("limit") int limit);

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NOT NULL AND e.asset_id IS NOT NULL "
              + "AND e.inject_expectation_created_at >= :createdAfter "
              + "AND "
              + RESULTS_HAS_NO_RESULT_FOR_SOURCE
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findAgentExpectationsNotFilledForSourceCreatedAfter(
      @Param("tenantId") String tenantId,
      @Param("type") String type,
      @Param("sourceId") String sourceId,
      @Param("createdAfter") Instant createdAfter,
      @Param("limit") int limit);

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NOT NULL AND e.asset_id IS NOT NULL "
              + "AND e.inject_expectation_created_at >= :createdAfter "
              + "AND "
              + RESULTS_HAS_NO_RESULT_AT_ALL
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findAgentExpectationsNotFilledCreatedAfter(
      @Param("tenantId") String tenantId,
      @Param("type") String type,
      @Param("createdAfter") Instant createdAfter,
      @Param("limit") int limit);

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND "
              + RESULTS_HAS_NO_RESULT_FOR_SOURCE
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findExpectationsNotFilledForSource(
      @Param("tenantId") String tenantId,
      @Param("type") String type,
      @Param("sourceId") String sourceId,
      @Param("limit") int limit);

  // Agentless detection/prevention expectations (e.g. AI adversarial injects whose target is an AI
  // model/agent rather than an endpoint with an installed agent). Used by AI defense collectors
  // (LLM firewall / guardrail) which correlate via the per-inject AI request marker.
  // Only LEAF asset expectations are returned (asset_id IS NOT NULL): asset-group parents
  // (asset_id NULL, asset_group_id set) are never fulfilled directly - their score is recomputed by
  // propagation from the asset leaves - and updating them directly would dereference a null asset.
  // Two additional guards keep collectors away from expectations that are not theirs to answer:
  // 1. PARENT asset expectations (same shape: asset set, agent null) whose asset has agent-level
  //    children are excluded - their score is derived from the agents, and a collector answering
  //    the parent directly would clobber that derived verdict (e.g. an LLM firewall stamping
  //    "Not Detected" on an endpoint parent whose EDR agents were already green).
  // 2. When the expectation restricts its expected security platform types, only collectors whose
  //    security platform matches one of those types receive it (empty/null means any platform).
  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NULL "
              + "AND e.asset_id IS NOT NULL "
              + "AND NOT EXISTS (SELECT 1 FROM injects_expectations child "
              + "  WHERE child.inject_id = e.inject_id "
              + "  AND child.asset_id = e.asset_id "
              + "  AND child.inject_expectation_type = e.inject_expectation_type "
              + "  AND child.agent_id IS NOT NULL) "
              + "AND (e.inject_expectation_expected_security_platforms IS NULL "
              + "  OR jsonb_typeof(e.inject_expectation_expected_security_platforms::jsonb) <> 'array' "
              + "  OR jsonb_array_length(e.inject_expectation_expected_security_platforms::jsonb) = 0 "
              + "  OR EXISTS (SELECT 1 FROM collectors c "
              + "    JOIN assets sp ON sp.asset_id = c.collector_security_platform "
              + "    WHERE c.collector_id = :sourceId "
              + "    AND sp.security_platform_type IS NOT NULL "
              + "    AND jsonb_exists(e.inject_expectation_expected_security_platforms::jsonb, sp.security_platform_type))) "
              + "AND "
              + RESULTS_HAS_NO_RESULT_FOR_SOURCE
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findAgentlessExpectationsNotFilledForSource(
      @Param("tenantId") String tenantId,
      @Param("type") String type,
      @Param("sourceId") String sourceId,
      @Param("limit") int limit);

  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND "
              + RESULTS_HAS_NO_RESULT_AT_ALL
              + "ORDER BY e.inject_expectation_created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findExpectationsNotFilled(
      @Param("tenantId") String tenantId, @Param("type") String type, @Param("limit") int limit);

  /** Finds unfilled and expired expectations for the expiration manager, scoped by tenant. */
  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_score IS NULL "
              + "AND (e.agent_id IS NOT NULL OR "
              + RESULTS_HAS_NO_RESULT_AT_ALL
              + ") "
              + "AND e.inject_expectation_created_at + (e.inject_expiration_time * interval '1 second') < now() "
              + "ORDER BY e.inject_expectation_created_at ASC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<BaseInjectExpectation> findExpectationsNotFilledAndExpired(
      @Param("tenantId") String tenantId, @Param("limit") int limit);

  @Query(value = "select i from InjectExpectation i where i.exercise.id = :exerciseId")
  List<BaseInjectExpectation> findAllForExercise(@Param("exerciseId") String exerciseId);

  @Query(value = "select i from InjectExpectation i where i.inject.id = :injectId")
  List<BaseInjectExpectation> findAllByInjectId(@Param("injectId") @NotBlank final String injectId);

  /**
   * Pre-loads agent / asset / assetGroup relations of all {@link TechnicalInjectExpectation}
   * instances for a given inject into the Hibernate session cache via JOIN FETCH. Must be called
   * before {@link #findAllByInjectId(String, String)} so that when the native query returns the
   * same rows, Hibernate serves already-hydrated instances instead of issuing extra lazy-load
   * queries for each relation.
   */
  @Query(
      "SELECT t FROM TechnicalInjectExpectation t "
          + "LEFT JOIN FETCH t.agent "
          + "LEFT JOIN FETCH t.asset "
          + "LEFT JOIN FETCH t.assetGroup "
          + "WHERE t.inject.id = :injectId "
          + "AND t.inject.tenant.id = :tenantId")
  List<BaseInjectExpectation> findTechnicalByInjectIdWithRelations(
      @Param("injectId") @NotBlank String injectId, @Param("tenantId") @NotBlank String tenantId);

  @Query(
      value =
          "SELECT i.* FROM injects_expectations i "
              + "WHERE i.exercise_id = :exerciseId AND i.inject_id = :injectId",
      nativeQuery = true)
  List<BaseInjectExpectation> findAllForExerciseAndInject(
      @Param("exerciseId") @NotBlank final String exerciseId,
      @Param("injectId") @NotBlank final String injectId);

  @Query(
      value =
          "select i from InjectExpectation i where i.exercise.id = :exerciseId "
              + "and i.type = 'CHALLENGE' and i.user.id = :userId ")
  List<ChallengeInjectExpectation> findChallengeExpectationsByExerciseAndUser(
      @Param("exerciseId") String exerciseId, @Param("userId") String userId);

  @Query(
      value =
          "select i from InjectExpectation i where i.user.id = :userId and i.exercise.id = :exerciseId "
              + "and i.challenge.id = :challengeId and i.type = 'CHALLENGE' ")
  List<ChallengeInjectExpectation> findByUserAndExerciseAndChallenge(
      @Param("userId") String userId,
      @Param("exerciseId") String exerciseId,
      @Param("challengeId") String challengeId);

  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id in (:injectIds) "
              + "and i.article.id in (:articlesIds) and i.team.id in (:teamIds) and i.type = 'ARTICLE'")
  List<ArticleInjectExpectation> findChannelExpectations(
      @Param("injectIds") List<String> injectIds,
      @Param("teamIds") List<String> teamIds,
      @Param("articlesIds") List<String> articlesIds);

  // -- BY TARGET TYPE

  @Query(
      value =
          "select i from InjectExpectation i "
              + "where i.inject.id = :injectId "
              + "and i.user.id = :playerId "
              + "ORDER BY i.type, i.createdAt")
  List<BaseInjectExpectation> findAllByInjectAndPlayer(
      @Param("injectId") @NotBlank final String injectId,
      @Param("playerId") @NotBlank final String playerId);

  // -- RETRIEVE EXPECTATIONS FOR TEAM AND NOT FOR PLAYERS
  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id = :injectId and i.team.id = :teamId and i.user is null")
  List<BaseInjectExpectation> findAllByInjectAndTeam(
      @Param("injectId") @NotBlank final String injectId,
      @Param("teamId") @NotBlank final String teamId);

  // -- BY AGENT / ASSET / ASSET GROUP TARGET --
  // These queries select from the base InjectExpectation entity: agent/asset
  // targets can carry MANUAL expectations too, so the results MUST be typed as
  // BaseInjectExpectation (typing them TechnicalInjectExpectation makes Spring
  // Data throw a ConversionFailedException as soon as a manual row matches).

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.agent.id = :agentId "
              + "ORDER BY i.type, i.createdAt")
  List<BaseInjectExpectation> findAllByInjectAndAgent(
      @Param("injectId") @NotBlank String injectId, @Param("agentId") @NotBlank String agentId);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.asset.id = :assetId "
              + "AND i.agent IS NULL "
              + "ORDER BY i.type, i.createdAt")
  List<BaseInjectExpectation> findAllByInjectAndAsset(
      @Param("injectId") @NotBlank String injectId, @Param("assetId") @NotBlank String assetId);

  // Agent-level expectation rows of an asset (any technical type). Used to mirror
  // the agents' security-platform results onto the asset target-results view.
  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.asset.id = :assetId "
              + "AND i.agent IS NOT NULL "
              + "ORDER BY i.type, i.createdAt")
  List<BaseInjectExpectation> findAllAgentExpectationsByInjectAndAsset(
      @Param("injectId") @NotBlank String injectId, @Param("assetId") @NotBlank String assetId);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.asset.id = :assetId "
              + "AND i.type = :expectationType "
              + "AND i.agent IS NOT NULL "
              + "ORDER BY i.type, i.createdAt")
  List<TechnicalInjectExpectation> findAllWithAgentsByInjectAndAsset(
      @Param("injectId") @NotBlank String injectId,
      @Param("assetId") @NotBlank String assetId,
      @Param("expectationType") @NotBlank BaseInjectExpectation.EXPECTATION_TYPE expectationType);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.assetGroup.id = :assetGroupId "
              + "AND i.asset IS NULL "
              + "AND i.agent IS NULL ")
  List<BaseInjectExpectation> findAllByInjectAndAssetGroup(
      @Param("injectId") @NotBlank final String injectId,
      @Param("assetGroupId") @NotBlank final String assetGroupId);

  @Query(
      value =
          "SELECT i.inject_id, i.exercise_id, "
              + "i.inject_expectation_type, i.inject_expectation_score, "
              + "i.inject_expectation_expected_score "
              + "FROM injects_expectations i "
              + "WHERE i.inject_id IN (:injectIds) "
              + "AND i.user_id IS NULL AND i.agent_id IS NULL "
              + "AND (i.asset_group_id IS NOT NULL "
              + "  OR (i.asset_id IS NOT NULL AND i.asset_id IN ("
              + "    SELECT ia.asset_id FROM injects_assets ia WHERE ia.inject_id = i.inject_id)) "
              + "  OR (i.asset_id IS NULL AND i.asset_group_id IS NULL))",
      nativeQuery = true)
  // Only primary expectations: asset-group, directly-targeted assets, and team-level.
  // Asset expectations that exist only because the asset belongs to a targeted group are excluded.
  List<RawGlobalScoreExpectation> rawForComputeGlobalByInjectIds(
      @Param("injectIds") Set<String> injectIds);

  @Query(
      value =
          "SELECT i.inject_id, i.exercise_id, "
              + "i.inject_expectation_type, i.inject_expectation_score, "
              + "i.inject_expectation_expected_score "
              + "FROM injects_expectations i "
              + "WHERE i.exercise_id IN (:exerciseIds) "
              + "AND i.user_id IS NULL AND i.agent_id IS NULL "
              + "AND (i.asset_group_id IS NOT NULL "
              + "  OR (i.asset_id IS NOT NULL AND i.asset_id IN ("
              + "    SELECT ia.asset_id FROM injects_assets ia WHERE ia.inject_id = i.inject_id)) "
              + "  OR (i.asset_id IS NULL AND i.asset_group_id IS NULL))",
      nativeQuery = true)
  // Only primary expectations: asset-group, directly-targeted assets, and team-level.
  List<RawGlobalScoreExpectation> rawForComputeGlobalByExerciseIds(
      @Param("exerciseIds") Set<String> exerciseIds);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i WHERE i.inject.id IN :injectIds"
              + " AND i.agent IS NULL AND i.user IS NULL"
              + " AND ("
              + "   i.assetGroup IS NOT NULL"
              + "   OR (i.asset IS NOT NULL AND i.asset.id IN ("
              + "     SELECT a.id FROM Inject inj JOIN inj.assets a WHERE inj.id = i.inject.id"
              + "   ))"
              + "   OR (i.asset IS NULL AND i.assetGroup IS NULL)"
              + " )")
  List<BaseInjectExpectation> findAllForGlobalScoreByInjects(
      @Param("injectIds") Set<String> injectIds);

  // -- INDEXING --

  @Query(
      value =
          """
    WITH changed_expectations AS (
        -- Per-player rows (user_id NOT NULL) are excluded everywhere: the team-level row already
        -- represents the players (same rule as the global-score queries above), and indexing both
        -- would double-count every manual expectation in dashboard statistics.
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          WHERE ie.agent_id IS NULL AND ie.user_id IS NULL
            AND ie.inject_expectation_updated_at > :from
        UNION
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          JOIN injects i ON i.inject_id = ie.inject_id
          WHERE ie.agent_id IS NULL AND ie.user_id IS NULL AND i.inject_updated_at > :from
        UNION
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          JOIN injects i ON i.inject_id = ie.inject_id
          JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
                                     AND ic.tenant_id = i.tenant_id
          WHERE ie.agent_id IS NULL AND ie.user_id IS NULL
            AND ic.injector_contract_updated_at > :from
        UNION
        -- Parent (agentless) expectation must be reindexed when one of its agent-level
        -- children changed. EXISTS avoids the parent x child cartesian self-join.
        SELECT parent_ie.inject_expectation_id
          FROM injects_expectations parent_ie
          WHERE parent_ie.agent_id IS NULL AND parent_ie.user_id IS NULL
            AND EXISTS (
              SELECT 1 FROM injects_expectations child_ie
              WHERE child_ie.inject_id = parent_ie.inject_id
                AND child_ie.agent_id IS NOT NULL
                AND child_ie.inject_expectation_updated_at > :from)
    ),
    ranked_expectations AS (
        SELECT ce.inject_expectation_id,
               GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, COALESCE(ic.injector_contract_updated_at, ie.inject_expectation_updated_at)) AS sort_ts
        FROM changed_expectations ce
        JOIN injects_expectations ie ON ie.inject_expectation_id = ce.inject_expectation_id
        JOIN injects i ON i.inject_id = ie.inject_id
        LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
                                        AND ic.tenant_id = i.tenant_id
        WHERE GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, COALESCE(ic.injector_contract_updated_at, ie.inject_expectation_updated_at)) > :from
        ORDER BY sort_ts ASC
        LIMIT :limit
    ),
    base AS (
        -- One row per ranked expectation (1:1 joins only — no fan-out).
        SELECT ie.inject_expectation_id, ie.inject_expectation_name, ie.inject_expectation_description, ie.inject_expectation_type,
               ie.inject_expectation_results, ie.inject_expectation_score, ie.inject_expectation_expected_score, ie.inject_expiration_time,
               ie.inject_expectation_group, ie.inject_expectation_created_at,
               ie.exercise_id, ie.inject_id, ie.user_id, ie.team_id, ie.agent_id, ie.asset_id, ie.asset_group_id,
               i.tenant_id, i.inject_title, i.inject_injector_contract AS contract_id,
               GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, COALESCE(ic.injector_contract_updated_at, ie.inject_expectation_updated_at)) AS inject_expectation_updated_at
        FROM injects_expectations ie
        JOIN ranked_expectations re ON ie.inject_expectation_id = re.inject_expectation_id
        LEFT JOIN injects i ON i.inject_id = ie.inject_id
        LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
                                        AND ic.tenant_id = i.tenant_id
    ),
    ap_agg AS (
        SELECT ic_ap.injector_contract_id, array_agg(DISTINCT ic_ap.attack_pattern_id) AS attack_pattern_ids
        FROM injectors_contracts_attack_patterns ic_ap
        WHERE ic_ap.injector_contract_id IN (SELECT contract_id FROM base WHERE contract_id IS NOT NULL)
        GROUP BY ic_ap.injector_contract_id
    ),
    dom_agg AS (
        SELECT ic_d.injector_contract_id, array_agg(DISTINCT ic_d.domain_id) AS domain_ids
        FROM injectors_contracts_domains ic_d
        WHERE ic_d.injector_contract_id IN (SELECT contract_id FROM base WHERE contract_id IS NOT NULL)
        GROUP BY ic_d.injector_contract_id
    ),
    track_agg AS (
        SELECT ins.status_inject AS inject_id, max(ins.tracking_sent_date) AS tracking_sent_date
        FROM injects_statuses ins
        WHERE ins.status_inject IN (SELECT inject_id FROM base WHERE inject_id IS NOT NULL)
        GROUP BY ins.status_inject
    ),
    scen_agg AS (
        SELECT se.exercise_id, max(se.scenario_id) AS scenario_id
        FROM scenarios_exercises se
        WHERE se.exercise_id IN (SELECT exercise_id FROM base WHERE exercise_id IS NOT NULL)
        GROUP BY se.exercise_id
    ),
    sp_self AS (
        -- Collectors / assets referenced in THIS expectation own results.
        SELECT b.inject_expectation_id,
               COALESCE(array_agg(DISTINCT c.collector_security_platform::text) FILTER (WHERE c.collector_security_platform IS NOT NULL), ARRAY[]::text[])
               || COALESCE(array_agg(DISTINCT a.asset_id::text) FILTER (WHERE a.asset_id IS NOT NULL), ARRAY[]::text[]) AS ids
        FROM base b
        LEFT JOIN LATERAL jsonb_array_elements(b.inject_expectation_results::jsonb) AS r(elem) ON true
        LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text
        LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
        GROUP BY b.inject_expectation_id
    ),
    agent_security_platforms AS (
        -- Security platforms contributed by agent-level children, scoped to the SAME expectation
        -- type and (when the parent is asset-level) the SAME asset as the parent doc. Keyed per
        -- parent expectation: joining only on inject_id would attribute every platform that
        -- returned any result for any expectation of the inject (e.g. blame a platform that
        -- detected for a prevention miss on another asset).
        SELECT b.inject_expectation_id,
               COALESCE(array_agg(DISTINCT child_c.collector_security_platform::text) FILTER (WHERE child_c.collector_security_platform IS NOT NULL), ARRAY[]::text[])
               || COALESCE(array_agg(DISTINCT child_a.asset_id::text) FILTER (WHERE child_a.asset_id IS NOT NULL), ARRAY[]::text[]) AS security_platform_ids
        FROM base b
        JOIN injects_expectations child_ie
          ON child_ie.inject_id = b.inject_id
         AND child_ie.agent_id IS NOT NULL
         AND child_ie.inject_expectation_type = b.inject_expectation_type
         AND (b.asset_id IS NULL OR child_ie.asset_id = b.asset_id)
        LEFT JOIN LATERAL jsonb_array_elements(child_ie.inject_expectation_results::jsonb) AS child_r(elem) ON true
        LEFT JOIN collectors child_c ON child_r.elem->>'sourceId' = child_c.collector_id::text
        LEFT JOIN assets child_a ON child_r.elem->>'sourceId' = child_a.asset_id::text
        GROUP BY b.inject_expectation_id
    )
    SELECT b.inject_expectation_id, b.inject_expectation_name, b.inject_expectation_description, b.inject_expectation_type,
           b.inject_expectation_results, b.inject_expectation_score, b.inject_expectation_expected_score, b.inject_expiration_time,
           b.inject_expectation_group, b.inject_expectation_created_at,
           b.inject_expectation_updated_at,
           b.exercise_id, b.inject_id, b.user_id, b.team_id, b.agent_id, b.asset_id, b.asset_group_id, b.tenant_id, b.inject_title,
           ta.tracking_sent_date,
           apa.attack_pattern_ids,
           da.domain_ids,
           sa.scenario_id,
           COALESCE(spself.ids, ARRAY[]::text[]) || COALESCE(asp.security_platform_ids, ARRAY[]::text[]) AS security_platform_ids
    FROM base b
    LEFT JOIN ap_agg apa ON apa.injector_contract_id = b.contract_id
    LEFT JOIN dom_agg da ON da.injector_contract_id = b.contract_id
    LEFT JOIN track_agg ta ON ta.inject_id = b.inject_id
    LEFT JOIN scen_agg sa ON sa.exercise_id = b.exercise_id
    LEFT JOIN sp_self spself ON spself.inject_expectation_id = b.inject_expectation_id
    LEFT JOIN agent_security_platforms asp ON asp.inject_expectation_id = b.inject_expectation_id
    WHERE b.agent_id IS NULL
    ORDER BY b.inject_expectation_updated_at ASC
    """,
      nativeQuery = true)
  List<RawInjectExpectationIndexing> findForIndexing(
      @Param("from") Instant from, @Param("limit") int limit);

  /**
   * Retrieves a set of distinct inject IDs associated with the specified inject expectation IDs.
   *
   * @param expectationIds the set of inject expectation IDs to filter by
   * @return a set of distinct inject IDs linked to the given expectation IDs
   */
  @Query(
      """
      SELECT DISTINCT ie.inject.id
      FROM InjectExpectation ie
      WHERE ie.id IN :expectationIds
      """)
  Set<String> findDistinctInjectIdsByInjectExpectationIds(
      @Param("expectationIds") Set<String> expectationIds);
}
