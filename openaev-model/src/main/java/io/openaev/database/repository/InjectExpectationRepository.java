package io.openaev.database.repository;

import io.openaev.database.model.ArticleInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
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
  String RESULTS_HAS_NO_RESULT_FOR_SOURCE =
      "NOT EXISTS (SELECT 1 FROM jsonb_array_elements(e.inject_expectation_results::jsonb) r "
          + "WHERE r->>'sourceId' = :sourceId AND COALESCE(r->>'result', '') <> '') ";
  String RESULTS_HAS_NO_RESULT_AT_ALL =
      "NOT EXISTS (SELECT 1 FROM jsonb_array_elements(e.inject_expectation_results::jsonb) r "
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
  @Query(
      value =
          "SELECT e.* FROM injects_expectations e "
              + "JOIN injects i ON i.inject_id = e.inject_id "
              + "WHERE i.tenant_id = :tenantId "
              + "AND e.inject_expectation_type = :type "
              + "AND e.agent_id IS NULL "
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

  @Query(value = "select i from InjectExpectation i where i.exercise.id = :exerciseId")
  List<BaseInjectExpectation> findAllForExercise(@Param("exerciseId") String exerciseId);

  @Query(value = "select i from InjectExpectation i where i.inject.id = :injectId")
  List<BaseInjectExpectation> findAllByInjectId(@Param("injectId") @NotBlank final String injectId);

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

  // -- INJECT EXPECTATION TECHNICAL --

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.agent.id = :agentId "
              + "ORDER BY i.type, i.createdAt")
  List<TechnicalInjectExpectation> findAllByInjectAndAgent(
      @Param("injectId") @NotBlank String injectId, @Param("agentId") @NotBlank String agentId);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.asset.id = :assetId "
              + "AND i.agent IS NULL "
              + "ORDER BY i.type, i.createdAt")
  List<TechnicalInjectExpectation> findAllByInjectAndAsset(
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
  List<TechnicalInjectExpectation> findAllByInjectAndAssetGroup(
      @Param("injectId") @NotBlank final String injectId,
      @Param("assetGroupId") @NotBlank final String assetGroupId);

  @Query(
      value =
          "SELECT "
              + "i.inject_expectation_id AS inject_expectation_id, "
              + "i.inject_id AS inject_id, "
              + "i.exercise_id AS exercise_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.inject_expectation_type AS inject_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.inject_expectation_score AS inject_expectation_score, "
              + "i.inject_expectation_results AS inject_expectation_results, "
              + "i.inject_expectation_expected_score AS inject_expectation_expected_score, "
              + "i.inject_expectation_group AS inject_expectation_group "
              + "FROM injects_expectations i "
              + "WHERE i.inject_id IN (:injectIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, neither for agents, if applicable
  List<RawInjectExpectationIndexing> rawForComputeGlobalByInjectIds(
      @Param("injectIds") Set<String> injectIds);

  @Query(
      value =
          "SELECT "
              + "i.inject_expectation_id AS inject_expectation_id, "
              + "i.inject_id AS inject_id, "
              + "i.exercise_id AS exercise_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.inject_expectation_type AS inject_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.inject_expectation_score AS inject_expectation_score, "
              + "i.inject_expectation_expected_score AS inject_expectation_expected_score, "
              + "i.inject_expectation_group AS inject_expectation_group "
              + "FROM injects_expectations i "
              + "WHERE i.exercise_id IN (:exerciseIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, if applicable
  List<RawInjectExpectationIndexing> rawForComputeGlobalByExerciseIds(
      @Param("exerciseIds") Set<String> exerciseIds);

  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id in :injectIds and i.agent is null and i.user is null")
  List<BaseInjectExpectation> findAllForGlobalScoreByInjects(
      @Param("injectIds") Set<String> injectIds);

  // -- INDEXING --

  @Query(
      value =
          """
    WITH changed_expectations AS (
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          WHERE ie.agent_id IS NULL AND ie.inject_expectation_updated_at > :from
        UNION
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          JOIN injects i ON i.inject_id = ie.inject_id
          WHERE ie.agent_id IS NULL AND i.inject_updated_at > :from
        UNION
        SELECT ie.inject_expectation_id FROM injects_expectations ie
          JOIN injects i ON i.inject_id = ie.inject_id
          JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
          WHERE ie.agent_id IS NULL AND ic.injector_contract_updated_at > :from
        UNION
        SELECT parent_ie.inject_expectation_id
          FROM injects_expectations parent_ie
          JOIN injects_expectations child_ie ON child_ie.inject_id = parent_ie.inject_id
          WHERE parent_ie.agent_id IS NULL
            AND child_ie.agent_id IS NOT NULL
            AND child_ie.inject_expectation_updated_at > :from
    ),
    ranked_expectations AS (
      SELECT ce.inject_expectation_id,
        GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, COALESCE(ic.injector_contract_updated_at, ie.inject_expectation_updated_at)) AS sort_ts
      FROM changed_expectations ce
      JOIN injects_expectations ie ON ie.inject_expectation_id = ce.inject_expectation_id
      JOIN injects i ON i.inject_id = ie.inject_id
      LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
      ORDER BY sort_ts ASC
      LIMIT :limit
    ),
    agent_security_platforms AS (
      SELECT
          child_ie.inject_id,
          COALESCE(
              array_agg(DISTINCT child_c.collector_security_platform::text)
                  FILTER ( WHERE child_c.collector_security_platform IS NOT NULL ),
              ARRAY[]::text[]
          )
          || COALESCE(
              array_agg(DISTINCT child_a.asset_id::text)
                  FILTER ( WHERE child_a.asset_id IS NOT NULL ),
              ARRAY[]::text[]
          ) AS security_platform_ids
      FROM injects_expectations child_ie
      LEFT JOIN LATERAL jsonb_array_elements(child_ie.inject_expectation_results::jsonb) AS child_r(elem) ON true
      LEFT JOIN collectors child_c ON child_r.elem->>'sourceId' = child_c.collector_id::text
      LEFT JOIN assets child_a ON child_r.elem->>'sourceId' = child_a.asset_id::text
      WHERE child_ie.agent_id IS NOT NULL
      GROUP BY child_ie.inject_id),
    inject_expectation_data AS (
      SELECT
      ie.inject_expectation_id,
      ie.inject_expectation_name,
      ie.inject_expectation_description,
      ie.inject_expectation_type,
      ie.inject_expectation_results,
      ie.inject_expectation_score,
      ie.inject_expectation_expected_score,
      ie.inject_expiration_time,
      ie.inject_expectation_group,
      ie.inject_expectation_created_at,
      GREATEST(ie.inject_expectation_updated_at, max(i.inject_updated_at), max(ic.injector_contract_updated_at)) as inject_expectation_updated_at,
      ie.exercise_id,
      ie.inject_id,
      ie.user_id,
      ie.team_id,
      ie.agent_id,
      ie.asset_id,
      ie.asset_group_id,
      i.tenant_id,
      i.inject_title as inject_title,
      MAX(ins.tracking_sent_date) AS tracking_sent_date,
      array_agg(DISTINCT ap.attack_pattern_id) FILTER ( WHERE ap.attack_pattern_id IS NOT NULL ) AS attack_pattern_ids,
      array_agg(DISTINCT ic_d.domain_id) FILTER (WHERE ic_d.domain_id IS NOT NULL ) AS domain_ids,
      MAX(se.scenario_id) AS scenario_id,
      COALESCE(
          array_agg(DISTINCT c.collector_security_platform::text)
              FILTER ( WHERE c.collector_security_platform IS NOT NULL ),
          ARRAY[]::text[]
      )
      || COALESCE(
          array_agg(DISTINCT a.asset_id::text)
              FILTER ( WHERE a.asset_id IS NOT NULL ),
          ARRAY[]::text[]
      )
      || COALESCE(asp.security_platform_ids, ARRAY[]::text[]) AS security_platform_ids
    FROM injects_expectations ie
    JOIN ranked_expectations re ON ie.inject_expectation_id = re.inject_expectation_id
    LEFT JOIN exercises ex ON ex.exercise_id = ie.exercise_id
    LEFT JOIN injects i ON i.inject_id = ie.inject_id
    LEFT JOIN injects_statuses ins ON ins.status_inject = i.inject_id
    LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
    LEFT JOIN injectors_contracts_attack_patterns ic_ap ON ic_ap.injector_contract_id = ic.injector_contract_id
    LEFT JOIN attack_patterns ap ON ap.attack_pattern_id = ic_ap.attack_pattern_id
    LEFT JOIN injectors_contracts_domains ic_d ON ic_d.injector_contract_id = ic.injector_contract_id
    LEFT JOIN teams t ON t.team_id = ie.team_id
    LEFT JOIN assets asset ON asset.asset_id = ie.asset_id
    LEFT JOIN asset_groups ag ON ag.asset_group_id = ie.asset_group_id
    LEFT JOIN scenarios_exercises se ON se.exercise_id = ie.exercise_id
    LEFT JOIN LATERAL jsonb_array_elements(ie.inject_expectation_results::jsonb) AS r(elem) ON true
    LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text
    LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
    LEFT JOIN agent_security_platforms asp ON asp.inject_id = ie.inject_id
    GROUP BY
      ie.inject_expectation_id,
      ic.injector_contract_id,
      i.inject_title,
      i.tenant_id,
      asp.security_platform_ids
    )
    SELECT * FROM inject_expectation_data ied
    WHERE ied.agent_id IS NULL
    ORDER BY ied.inject_expectation_updated_at ASC
    """,
      nativeQuery = true)
  List<RawInjectExpectationIndexing> findForIndexing(
      @Param("from") Instant from, @Param("limit") int limit);

  /**
   * Fetches inject expectations updated after {@code from}, using a compound keyset cursor when
   * {@code lastId} is non-null to avoid skipping items that share the same {@code updated_at}
   * timestamp across batch boundaries.
   *
   * <p>When {@code lastId} is {@code null} the query degrades to a simple {@code > :from} cursor
   * (first-batch behaviour). When non-null it additionally returns rows at exactly {@code from}
   * whose ID is strictly greater than {@code lastId}.
   */
  @Query(
      value =
          """
    WITH changed_expectations AS (
      SELECT ie.inject_expectation_id FROM injects_expectations ie
        WHERE ie.inject_expectation_updated_at > :from
        OR (:lastId IS NOT NULL AND ie.inject_expectation_updated_at = :from AND ie.inject_expectation_id > :lastId)
      UNION
      SELECT ie.inject_expectation_id FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        WHERE i.inject_updated_at > :from
        OR (:lastId IS NOT NULL AND i.inject_updated_at = :from AND ie.inject_expectation_id > :lastId)
      UNION
      SELECT ie.inject_expectation_id FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
        WHERE ic.injector_contract_updated_at > :from
        OR (:lastId IS NOT NULL AND ic.injector_contract_updated_at = :from AND ie.inject_expectation_id > :lastId)
    ),
    inject_expectation_data AS (
      SELECT
      ie.inject_expectation_id,
      ie.inject_expectation_name,
      ie.inject_expectation_description,
      ie.inject_expectation_type,
      ie.inject_expectation_results,
      ie.inject_expectation_score,
      ie.inject_expectation_expected_score,
      ie.inject_expiration_time,
      ie.inject_expectation_group,
      ie.inject_expectation_created_at,
      GREATEST(ie.inject_expectation_updated_at, max(i.inject_updated_at), max(ic.injector_contract_updated_at)) as inject_expectation_updated_at,
      ie.exercise_id,
      ie.inject_id,
      ie.user_id,
      ie.team_id,
      ie.agent_id,
      ie.asset_id,
      ie.asset_group_id,
      i.tenant_id,
      i.inject_title as inject_title,
      MAX(ins.tracking_sent_date) AS tracking_sent_date,
      array_agg(DISTINCT ap.attack_pattern_id) FILTER ( WHERE ap.attack_pattern_id IS NOT NULL ) AS attack_pattern_ids,
      array_agg(DISTINCT ic_d.domain_id) FILTER (WHERE ic_d.domain_id IS NOT NULL ) AS domain_ids,
      MAX(se.scenario_id) AS scenario_id,
      array_agg(DISTINCT c.collector_security_platform) FILTER ( WHERE c.collector_security_platform IS NOT NULL ) ||
      array_agg(DISTINCT a.asset_id) FILTER ( WHERE a.asset_id IS NOT NULL ) AS security_platform_ids
    FROM injects_expectations ie
    JOIN changed_expectations ce ON ie.inject_expectation_id = ce.inject_expectation_id
    LEFT JOIN exercises ex ON ex.exercise_id = ie.exercise_id
    LEFT JOIN injects i ON i.inject_id = ie.inject_id
    LEFT JOIN injects_statuses ins ON ins.status_inject = i.inject_id
    LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
    LEFT JOIN injectors_contracts_attack_patterns ic_ap ON ic_ap.injector_contract_id = ic.injector_contract_id
    LEFT JOIN attack_patterns ap ON ap.attack_pattern_id = ic_ap.attack_pattern_id
    LEFT JOIN injectors_contracts_domains ic_d ON ic_d.injector_contract_id = ic.injector_contract_id
    LEFT JOIN users u ON u.user_id = ie.user_id
    LEFT JOIN teams t ON t.team_id = ie.team_id
    LEFT JOIN assets asset ON asset.asset_id = ie.asset_id
    LEFT JOIN asset_groups ag ON ag.asset_group_id = ie.asset_group_id
    LEFT JOIN scenarios_exercises se ON se.exercise_id = ie.exercise_id
    LEFT JOIN LATERAL jsonb_array_elements(ie.inject_expectation_results::jsonb) AS r(elem) ON true
    LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text
    LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
    GROUP BY
      ie.inject_expectation_id,
      ic.injector_contract_id,
      i.inject_title,
      i.tenant_id
    )
    SELECT * FROM inject_expectation_data ied
    WHERE ied.agent_id IS NULL
    ORDER BY ied.inject_expectation_updated_at ASC, ied.inject_expectation_id ASC
    LIMIT :limit
    """,
      nativeQuery = true)
  List<RawInjectExpectationIndexing> findForIndexingAfter(
      @Param("from") Instant from, @Param("lastId") String lastId, @Param("limit") int limit);

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
