package io.openaev.database.repository;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.raw.RawAttackObservationIndexing;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackObservationRepository extends JpaRepository<BaseInjectExpectation, String> {

  // Mirrored by existsPendingIndexing below: keep the selection predicate (agent_id IS NULL,
  // expectation type, scenario presence, timestamp expression) identical in both queries.
  @Query(
      value =
          """
    WITH changed_expectations AS (
        -- Sargable prefilter, same shape as InjectExpectationRepository.changed_expectations: the
        -- GREATEST() of `touched` cannot use an index, so the candidate set is narrowed first
        -- through idx_injects_expectations_indexing_cursor, idx_injects_updated_at and
        -- idx_exercises_updated_at. `>=` and not `>`: the tiebreak branch of `touched` selects rows
        -- exactly on :fromTs.
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        WHERE ie.agent_id IS NULL AND ie.inject_expectation_updated_at >= :fromTs
      UNION
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        WHERE ie.agent_id IS NULL AND i.inject_updated_at >= :fromTs
      UNION
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        JOIN exercises e ON e.exercise_id = i.inject_exercise
        WHERE ie.agent_id IS NULL AND e.exercise_updated_at >= :fromTs
    ),
    touched AS (
        -- Keys whose verdict-bearing evidence moved past the cursor. LIMIT sits here, after both
        -- scope filters, so a batch cut short by LIMIT still makes forward progress. The tiebreak
        -- (grain md5) is constant within a group: a group with any surviving row past :fromTs
        -- already sorts after it on watermark alone - the id only decides ties exactly on :fromTs.
        SELECT
          i.tenant_id AS tenant_id,
          ie.asset_id AS asset_id,
          ap.attack_pattern_external_id AS attack_pattern_external_id,
          ie.inject_expectation_type AS inject_expectation_type,
          sa.scenario_id AS scenario_id,
          max(GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at)) AS watermark
        FROM injects_expectations ie
        JOIN changed_expectations ce ON ce.inject_expectation_id = ie.inject_expectation_id
        JOIN injects i ON i.inject_id = ie.inject_id
        JOIN exercises e ON e.exercise_id = i.inject_exercise
        -- One scenario per exercise: dedups the scenarios_exercises fan-out. Same max() as
        -- InjectExpectationRepository.scen_agg, but correlated so it stays an index lookup
        -- instead of aggregating the whole table on every batch. The aggregate always returns a
        -- row, so IS NOT NULL is what makes the join behave as INNER: no scenario, no document.
        JOIN LATERAL (
            SELECT max(se.scenario_id) AS scenario_id
            FROM scenarios_exercises se
            WHERE se.exercise_id = e.exercise_id
        ) sa ON sa.scenario_id IS NOT NULL
        -- Correlated on tenant_id: a built-in contract keeps the same id in every tenant, each
        -- tenant linking it to its own attack_patterns rows, so the contract id alone fans out.
        JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract AND icap.tenant_id = i.tenant_id
        JOIN attack_patterns ap ON ap.attack_pattern_id = icap.attack_pattern_id
        -- Joined here, before LIMIT, and again for its labels below: an asset the final join
        -- would drop must not be selected either, or a batch made only of such rows would emit
        -- nothing and the cursor would never move past it.
        JOIN assets ta ON ta.asset_id = ie.asset_id
        WHERE ie.agent_id IS NULL
          AND ie.asset_id IS NOT NULL
          AND ie.inject_expectation_type IN ('PREVENTION', 'DETECTION')
          AND i.inject_exercise IS NOT NULL
          AND ie.inject_expectation_score IS NOT NULL
          AND (
            GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at) > :fromTs
            OR (
              GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at) = :fromTs
              AND (
                CAST(:fromId AS text) IS NULL
                OR md5(i.tenant_id || '|' || ie.asset_id || '|' || ap.attack_pattern_external_id
                        || '|' || ie.inject_expectation_type || '|' || sa.scenario_id) > CAST(:fromId AS text)
              )
            )
          )
        GROUP BY i.tenant_id, ie.asset_id, ap.attack_pattern_external_id, ie.inject_expectation_type, sa.scenario_id
        ORDER BY watermark,
                 md5(i.tenant_id || '|' || ie.asset_id || '|' || ap.attack_pattern_external_id
                     || '|' || ie.inject_expectation_type || '|' || sa.scenario_id)
        LIMIT :limit
    ),
    latest AS (
        -- The latest replay of the scenario still carrying a verified verdict for this key: a
        -- running (PENDING) replay must not blank the previously verified state. exercise_id is
        -- a required tiebreak for determinism: exercise_start_date alone is not unique.
        SELECT
          t.tenant_id, t.asset_id, t.attack_pattern_external_id, t.inject_expectation_type, t.scenario_id,
          t.watermark,
          lr.exercise_id, lr.exercise_name, lr.exercise_start_date, lr.exercise_status,
          lr.attack_pattern_id, lr.attack_pattern_name
        FROM touched t
        JOIN LATERAL (
            -- Start date and status let a client tell when a later finished replay no longer
            -- plays this key, which keeps this older replay here. The status stays fresh:
            -- exercise_updated_at is part of the watermark.
            SELECT i.inject_exercise AS exercise_id, e.exercise_name AS exercise_name,
                   e.exercise_start_date AS exercise_start_date, e.exercise_status AS exercise_status,
                   ap2.attack_pattern_id AS attack_pattern_id, ap2.attack_pattern_name AS attack_pattern_name
            FROM injects_expectations ie
            JOIN injects i ON i.inject_id = ie.inject_id
            JOIN exercises e ON e.exercise_id = i.inject_exercise
            JOIN scenarios_exercises se2 ON se2.exercise_id = e.exercise_id AND se2.scenario_id = t.scenario_id
            JOIN injectors_contracts_attack_patterns icap2 ON icap2.injector_contract_id = i.inject_injector_contract AND icap2.tenant_id = i.tenant_id
            JOIN attack_patterns ap2 ON ap2.attack_pattern_id = icap2.attack_pattern_id
                                     AND ap2.attack_pattern_external_id = t.attack_pattern_external_id
            WHERE ie.agent_id IS NULL
              AND ie.asset_id = t.asset_id
              AND ie.inject_expectation_type = t.inject_expectation_type
              AND ie.inject_expectation_score IS NOT NULL
              AND i.tenant_id = t.tenant_id
            ORDER BY e.exercise_start_date DESC NULLS LAST, i.inject_exercise DESC
            LIMIT 1
        ) lr ON true
    ),
    matched AS (
        -- The parent (asset-level) expectation rows of the latest replay for the key: the
        -- population counted for the attempts and mined for security-platform attribution.
        SELECT
          l.tenant_id, l.asset_id, l.attack_pattern_external_id, l.inject_expectation_type, l.scenario_id,
          ie.inject_expectation_id, ie.inject_id, ie.inject_expectation_results,
          ie.inject_expectation_score, ie.inject_expectation_expected_score, ie.inject_expectation_updated_at
        FROM latest l
        JOIN injects_expectations ie
          ON ie.asset_id = l.asset_id
         AND ie.inject_expectation_type = l.inject_expectation_type
         AND ie.agent_id IS NULL
        JOIN injects i ON i.inject_id = ie.inject_id AND i.inject_exercise = l.exercise_id AND i.tenant_id = l.tenant_id
        JOIN injectors_contracts_attack_patterns icap3 ON icap3.injector_contract_id = i.inject_injector_contract AND icap3.tenant_id = i.tenant_id
        JOIN attack_patterns ap3 ON ap3.attack_pattern_id = icap3.attack_pattern_id
                                 AND ap3.attack_pattern_external_id = l.attack_pattern_external_id
    ),
    counters AS (
        -- Status and ratio computed with the exact score >= expectedScore predicate of
        -- InjectExpectationHelper.computeStatus. attempts_total is never zero: `latest` already
        -- required at least one verified row to resolve the replay.
        SELECT
          tenant_id, asset_id, attack_pattern_external_id, inject_expectation_type, scenario_id,
          count(*) FILTER (WHERE inject_expectation_score IS NOT NULL) AS attempts_total,
          count(*) FILTER (WHERE inject_expectation_score IS NOT NULL
                              AND inject_expectation_score >= inject_expectation_expected_score) AS attempts_success,
          -- Restricted to verified rows: an unverified sibling of the same replay must not drag
          -- the "last verified at" forward.
          max(inject_expectation_updated_at) FILTER (WHERE inject_expectation_score IS NOT NULL) AS last_verified_at
        FROM matched
        GROUP BY tenant_id, asset_id, attack_pattern_external_id, inject_expectation_type, scenario_id
    ),
    sp_self AS (
        -- Collectors / assets referenced in the own results of the parent. The collectors join is
        -- tenant-correlated exactly like InjectExpectationRepository.sp_self: the indexing sweep
        -- runs under an all-tenant scope and built-in collectors share collector_id across
        -- tenants, so a bare sourceId join would attribute a security platform of another tenant.
        -- NB: no apostrophe anywhere in these comments, Spring Data pre-parses the query string
        -- for SpEL and reads a lone quote as an unterminated string literal.
        -- succeeded_ids aggregates across every attempt of the replay, so it reads as "platforms
        -- that met the expected score at least once", not "on every attempt".
        -- The types are read from the sourcePlatform of each result (EDR, SIEM...), not from the
        -- joins above: it is the value clients key a verdict on, and it needs no lookup.
        SELECT
          m.tenant_id, m.asset_id, m.attack_pattern_external_id, m.inject_expectation_type, m.scenario_id,
          COALESCE(array_agg(DISTINCT c.collector_security_platform::text) FILTER (WHERE c.collector_security_platform IS NOT NULL), ARRAY[]::text[])
            || COALESCE(array_agg(DISTINCT a.asset_id::text) FILTER (WHERE a.asset_id IS NOT NULL), ARRAY[]::text[]) AS ids,
          COALESCE(array_agg(DISTINCT c.collector_security_platform::text) FILTER (
              WHERE c.collector_security_platform IS NOT NULL
                AND (r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[])
            || COALESCE(array_agg(DISTINCT a.asset_id::text) FILTER (
              WHERE a.asset_id IS NOT NULL
                AND (r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[]) AS succeeded_ids,
          COALESCE(array_agg(DISTINCT btrim(r.elem->>'sourcePlatform')) FILTER (
              WHERE length(btrim(r.elem->>'sourcePlatform')) > 0), ARRAY[]::text[]) AS types,
          COALESCE(array_agg(DISTINCT btrim(r.elem->>'sourcePlatform')) FILTER (
              WHERE length(btrim(r.elem->>'sourcePlatform')) > 0
                AND (r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[]) AS succeeded_types
        FROM matched m
        LEFT JOIN LATERAL jsonb_array_elements(m.inject_expectation_results::jsonb) AS r(elem) ON true
        LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text AND c.tenant_id = m.tenant_id
        LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
        GROUP BY m.tenant_id, m.asset_id, m.attack_pattern_external_id, m.inject_expectation_type, m.scenario_id
    ),
    agent_security_platforms AS (
        -- Security platforms contributed by agent-level children of the SAME inject/asset, same
        -- tenant-correlated collectors join as sp_self.
        SELECT
          m.tenant_id, m.asset_id, m.attack_pattern_external_id, m.inject_expectation_type, m.scenario_id,
          COALESCE(array_agg(DISTINCT child_c.collector_security_platform::text) FILTER (WHERE child_c.collector_security_platform IS NOT NULL), ARRAY[]::text[])
            || COALESCE(array_agg(DISTINCT child_a.asset_id::text) FILTER (WHERE child_a.asset_id IS NOT NULL), ARRAY[]::text[]) AS ids,
          COALESCE(array_agg(DISTINCT child_c.collector_security_platform::text) FILTER (
              WHERE child_c.collector_security_platform IS NOT NULL
                AND (child_r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[])
            || COALESCE(array_agg(DISTINCT child_a.asset_id::text) FILTER (
              WHERE child_a.asset_id IS NOT NULL
                AND (child_r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[]) AS succeeded_ids,
          COALESCE(array_agg(DISTINCT btrim(child_r.elem->>'sourcePlatform')) FILTER (
              WHERE length(btrim(child_r.elem->>'sourcePlatform')) > 0), ARRAY[]::text[]) AS types,
          COALESCE(array_agg(DISTINCT btrim(child_r.elem->>'sourcePlatform')) FILTER (
              WHERE length(btrim(child_r.elem->>'sourcePlatform')) > 0
                AND (child_r.elem->>'score')::double precision >= m.inject_expectation_expected_score), ARRAY[]::text[]) AS succeeded_types
        FROM matched m
        JOIN injects_expectations child_ie
          ON child_ie.inject_id = m.inject_id
         AND child_ie.agent_id IS NOT NULL
         AND child_ie.inject_expectation_type = m.inject_expectation_type
         AND child_ie.asset_id = m.asset_id
        LEFT JOIN LATERAL jsonb_array_elements(child_ie.inject_expectation_results::jsonb) AS child_r(elem) ON true
        LEFT JOIN collectors child_c ON child_r.elem->>'sourceId' = child_c.collector_id::text AND child_c.tenant_id = m.tenant_id
        LEFT JOIN assets child_a ON child_r.elem->>'sourceId' = child_a.asset_id::text
        GROUP BY m.tenant_id, m.asset_id, m.attack_pattern_external_id, m.inject_expectation_type, m.scenario_id
    )
    SELECT
      md5(l.tenant_id || '|' || l.asset_id || '|' || l.attack_pattern_external_id || '|'
          || l.inject_expectation_type || '|' || l.scenario_id) AS base_id,
      l.tenant_id AS tenant_id,
      l.watermark AS base_updated_at,
      l.asset_id AS base_asset_side,
      l.scenario_id AS base_scenario_side,
      l.exercise_id AS base_simulation_side,
      l.attack_pattern_id AS attack_pattern_id,
      COALESCE(spself.ids, ARRAY[]::text[]) || COALESCE(asp.ids, ARRAY[]::text[]) AS security_platform_ids,
      COALESCE(spself.succeeded_ids, ARRAY[]::text[]) || COALESCE(asp.succeeded_ids, ARRAY[]::text[]) AS platforms_succeeded_ids,
      COALESCE(spself.types, ARRAY[]::text[]) || COALESCE(asp.types, ARRAY[]::text[]) AS security_platform_types,
      COALESCE(spself.succeeded_types, ARRAY[]::text[]) || COALESCE(asp.succeeded_types, ARRAY[]::text[]) AS platforms_succeeded_types,
      li.inject_id AS base_inject_side,
      a.asset_name AS asset_name,
      a.asset_hostname AS asset_hostname,
      a.endpoint_platform AS endpoint_platform,
      tn.tenant_name AS tenant_name,
      l.attack_pattern_external_id AS attack_pattern_external_id,
      l.attack_pattern_name AS attack_pattern_name,
      s.scenario_name AS scenario_name,
      l.exercise_name AS simulation_name,
      l.exercise_start_date AS simulation_start_date,
      l.exercise_status AS simulation_status,
      l.inject_expectation_type AS inject_expectation_type,
      -- This stream only holds verdicts, so PENDING and UNKNOWN are unreachable here: an attempt
      -- whose expected score is null counts as not succeeded rather than as UNKNOWN.
      CASE WHEN c.attempts_success = c.attempts_total THEN 'SUCCESS'
           WHEN c.attempts_success = 0 THEN 'FAILED'
           ELSE 'PARTIAL' END AS status,
      c.attempts_total AS attempts_total,
      c.attempts_success AS attempts_success,
      (c.attempts_success::double precision / c.attempts_total) AS coverage_ratio,
      c.last_verified_at AS last_verified_at
    FROM latest l
    JOIN counters c
      ON c.tenant_id = l.tenant_id AND c.asset_id = l.asset_id
     AND c.attack_pattern_external_id = l.attack_pattern_external_id
     AND c.inject_expectation_type = l.inject_expectation_type AND c.scenario_id = l.scenario_id
    JOIN assets a ON a.asset_id = l.asset_id
    JOIN scenarios s ON s.scenario_id = l.scenario_id
    JOIN tenants tn ON tn.tenant_id = l.tenant_id
    LEFT JOIN sp_self spself
      ON spself.tenant_id = l.tenant_id AND spself.asset_id = l.asset_id
     AND spself.attack_pattern_external_id = l.attack_pattern_external_id
     AND spself.inject_expectation_type = l.inject_expectation_type AND spself.scenario_id = l.scenario_id
    LEFT JOIN agent_security_platforms asp
      ON asp.tenant_id = l.tenant_id AND asp.asset_id = l.asset_id
     AND asp.attack_pattern_external_id = l.attack_pattern_external_id
     AND asp.inject_expectation_type = l.inject_expectation_type AND asp.scenario_id = l.scenario_id
    -- The inject behind last_verified_at, with inject_id as tiebreak: a representative to link
    -- to, since the grain can span several injects of the replay. A LATERAL rather than an
    -- ordered array_agg, which the tenant inspector parser cannot walk.
    LEFT JOIN LATERAL (
        SELECT m.inject_id
        FROM matched m
        WHERE m.tenant_id = l.tenant_id AND m.asset_id = l.asset_id
          AND m.attack_pattern_external_id = l.attack_pattern_external_id
          AND m.inject_expectation_type = l.inject_expectation_type AND m.scenario_id = l.scenario_id
          AND m.inject_expectation_score IS NOT NULL
        ORDER BY m.inject_expectation_updated_at DESC, m.inject_id DESC
        LIMIT 1
    ) li ON true
    ORDER BY base_updated_at, base_id
    """,
      nativeQuery = true)
  List<RawAttackObservationIndexing> findForIndexing(
      @Param("fromTs") Instant fromTs, @Param("fromId") String fromId, @Param("limit") int limit);

  // Mirrors the selection and resume predicates of the touched CTE of findForIndexing above: keep
  // both in sync by hand. The resume predicate must be the keyset one, fromId included: a strict
  // > cursorTs would miss the rest of a tie group sitting exactly on the cursor and report it
  // indexed.
  // The candidate set goes through the same per-table sargable prefilter as changed_expectations,
  // since an OR across three joined tables cannot use their indexes. Each branch is bounded on
  // both sides, the GREATEST() <= :upperTs below being served by no index, and UNION ALL is
  // enough: duplicates do not change an EXISTS, while a UNION builds the whole set first.
  // Scoped to the request tenant, which is sound against the global cursor: every row of that
  // tenant before the cursor is indexed, so nothing of this tenant pending in (cursor, upper]
  // does mean this tenant is indexed through upper.
  @Query(
      value =
          """
    WITH changed_expectations AS (
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        WHERE ie.agent_id IS NULL
          AND ie.inject_expectation_updated_at >= :cursorTs
          AND ie.inject_expectation_updated_at <= :upperTs
      UNION ALL
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        WHERE ie.agent_id IS NULL AND i.inject_updated_at >= :cursorTs AND i.inject_updated_at <= :upperTs
      UNION ALL
        SELECT ie.inject_expectation_id
        FROM injects_expectations ie
        JOIN injects i ON i.inject_id = ie.inject_id
        JOIN exercises e ON e.exercise_id = i.inject_exercise
        WHERE ie.agent_id IS NULL AND e.exercise_updated_at >= :cursorTs AND e.exercise_updated_at <= :upperTs
    )
    SELECT EXISTS (
      SELECT 1
      FROM injects_expectations ie
      JOIN changed_expectations ce ON ce.inject_expectation_id = ie.inject_expectation_id
      JOIN injects i ON i.inject_id = ie.inject_id
      JOIN exercises e ON e.exercise_id = i.inject_exercise
      JOIN LATERAL (
          SELECT max(se.scenario_id) AS scenario_id
          FROM scenarios_exercises se
          WHERE se.exercise_id = e.exercise_id
      ) sa ON sa.scenario_id IS NOT NULL
      JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract AND icap.tenant_id = i.tenant_id
      JOIN attack_patterns ap ON ap.attack_pattern_id = icap.attack_pattern_id
      JOIN assets ta ON ta.asset_id = ie.asset_id
      WHERE i.tenant_id = :tenantId
        AND ie.agent_id IS NULL
        AND ie.asset_id IS NOT NULL
        AND ie.inject_expectation_type IN ('PREVENTION', 'DETECTION')
        AND i.inject_exercise IS NOT NULL
        AND ie.inject_expectation_score IS NOT NULL
        AND GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at) <= :upperTs
        AND (
          GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at) > :cursorTs
          OR (
            GREATEST(ie.inject_expectation_updated_at, i.inject_updated_at, e.exercise_updated_at) = :cursorTs
            AND (
              CAST(:cursorId AS text) IS NULL
              OR md5(i.tenant_id || '|' || ie.asset_id || '|' || ap.attack_pattern_external_id
                      || '|' || ie.inject_expectation_type || '|' || sa.scenario_id) > CAST(:cursorId AS text)
            )
          )
        )
    )
    """,
      nativeQuery = true)
  boolean existsPendingIndexing(
      @Param("tenantId") String tenantId,
      @Param("cursorTs") Instant cursorTs,
      @Param("cursorId") String cursorId,
      @Param("upperTs") Instant upperTs);
}
