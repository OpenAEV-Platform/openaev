package io.openaev.database.repository;

import io.openaev.database.model.Endpoint;
import io.openaev.database.raw.RawVulnerableEndpointIndexing;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VulnerableEndpointRepository extends JpaRepository<Endpoint, String> {

  @Query(
      value =
          """
    WITH changed_vulnerable_endpoints AS (
        SELECT DISTINCT a.asset_id, i.inject_exercise
        FROM findings f
        JOIN findings_assets fa ON f.finding_id = fa.finding_id
        JOIN assets a ON a.asset_id = fa.asset_id
        JOIN injects i ON i.inject_id = f.finding_inject_id
        WHERE a.asset_updated_at > :from
          AND f.finding_type = 'CVE'
          AND a.asset_type = :#{T(io.openaev.database.model.AssetType.Values).ENDPOINT_TYPE}
        UNION
        SELECT DISTINCT a.asset_id, i.inject_exercise
        FROM findings f
        JOIN findings_assets fa ON f.finding_id = fa.finding_id
        JOIN assets a ON a.asset_id = fa.asset_id
        JOIN injects i ON i.inject_id = f.finding_inject_id
        JOIN exercises e ON i.inject_exercise = e.exercise_id
        WHERE e.exercise_updated_at > :from
          AND f.finding_type = 'CVE'
          AND a.asset_type = :#{T(io.openaev.database.model.AssetType.Values).ENDPOINT_TYPE}
        UNION
        -- A new/updated CVE finding must create or refresh the vulnerable-endpoint doc even when
        -- neither the asset nor the exercise row was touched.
        SELECT DISTINCT a.asset_id, i.inject_exercise
        FROM findings f
        JOIN findings_assets fa ON f.finding_id = fa.finding_id
        JOIN assets a ON a.asset_id = fa.asset_id
        JOIN injects i ON i.inject_id = f.finding_inject_id
        WHERE f.finding_updated_at > :from
          AND f.finding_type = 'CVE'
          AND a.asset_type = :#{T(io.openaev.database.model.AssetType.Values).ENDPOINT_TYPE}
    ),
    ranked_vulnerable_endpoints AS (
        SELECT cve.asset_id, cve.inject_exercise
        FROM changed_vulnerable_endpoints cve
        JOIN exercises e ON e.exercise_id = cve.inject_exercise
        JOIN assets a ON a.asset_id = cve.asset_id
        LEFT JOIN LATERAL (
            SELECT max(f.finding_updated_at) AS max_finding
            FROM findings f
            JOIN findings_assets fa ON fa.finding_id = f.finding_id AND fa.asset_id = cve.asset_id
            JOIN injects i ON i.inject_id = f.finding_inject_id AND i.inject_exercise = cve.inject_exercise
            WHERE f.finding_type = 'CVE'
        ) fm ON true
        WHERE GREATEST(e.exercise_updated_at, a.asset_updated_at, fm.max_finding) > :from
        ORDER BY GREATEST(e.exercise_updated_at, a.asset_updated_at, fm.max_finding) ASC
        LIMIT :limit
    )
    SELECT
      CONCAT(a.asset_id, '_', rve.inject_exercise) as base_id,
      a.asset_id as vulnerable_endpoint_id,
      rve.inject_exercise as vulnerable_endpoint_simulation,
      MAX(se.scenario_id) as vulnerable_endpoint_scenario,
      a.asset_hostname as vulnerable_endpoint_hostname,
      a.endpoint_platform as vulnerable_endpoint_platform,
      a.endpoint_is_eol as vulnerable_endpoint_eol,
      a.endpoint_arch as vulnerable_endpoint_architecture,
      a.tenant_id,
      e.exercise_created_at as vulnerable_endpoint_created_at,
      GREATEST(e.exercise_updated_at, a.asset_updated_at, max(f.finding_updated_at)) as vulnerable_endpoint_updated_at,
      array_agg(fa.finding_id) FILTER ( WHERE fa.finding_id IS NOT NULL ) as vulnerable_endpoint_findings,
      array_agg(distinct at.tag_id) FILTER ( WHERE at.tag_id IS NOT NULL ) as vulnerable_endpoint_tags,
      (SELECT array_agg(ag.agent_id) FILTER (WHERE ag.agent_id IS NOT NULL)
       FROM agents ag WHERE ag.agent_asset = a.asset_id) as vulnerable_endpoint_agents,
      array_agg(f.finding_id) FILTER ( WHERE f.finding_id IS NOT NULL AND f.finding_type = 'CVE' ) as vulnerable_endpoint_cves,
      (SELECT array_agg(ag.agent_status) FILTER (WHERE ag.agent_id IS NOT NULL)
       FROM agents ag WHERE ag.agent_asset = a.asset_id) as vulnerable_endpoint_agents_statuses,
      (SELECT array_agg(ag.agent_privilege) FILTER (WHERE ag.agent_id IS NOT NULL)
       FROM agents ag WHERE ag.agent_asset = a.asset_id) as vulnerable_endpoint_agents_privileges
    FROM ranked_vulnerable_endpoints rve
    JOIN assets a ON a.asset_id = rve.asset_id
    JOIN exercises e ON e.exercise_id = rve.inject_exercise
    LEFT JOIN scenarios_exercises se ON se.exercise_id = e.exercise_id
    LEFT JOIN assets_tags at ON a.asset_id = at.asset_id
    JOIN injects i ON i.inject_exercise = rve.inject_exercise
    JOIN findings f ON f.finding_inject_id = i.inject_id AND f.finding_type = 'CVE'
    JOIN findings_assets fa ON f.finding_id = fa.finding_id AND fa.asset_id = a.asset_id
    GROUP BY a.asset_id, rve.inject_exercise, e.exercise_updated_at, e.exercise_created_at, a.asset_updated_at
    ORDER BY GREATEST(e.exercise_updated_at, a.asset_updated_at, max(f.finding_updated_at)) ASC
    """,
      nativeQuery = true)
  List<RawVulnerableEndpointIndexing> findForIndexing(
      @Param("from") Instant from, @Param("limit") int limit);
}
