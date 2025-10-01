package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.raw.RawVulnerableEndpoint;
import io.openaev.utils.Constants;
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
          "WITH agents_per_asset AS ("
              + "SELECT a.asset_id, "
              + "array_agg(ag.agent_id) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_ids, "
              + "array_agg(ag.agent_privilege) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_privs, "
              + "array_agg(ag.agent_last_seen) FILTER ( WHERE ag.agent_id IS NOT NULL ) as agent_last_seen "
              + "FROM assets a LEFT JOIN agents ag ON a.asset_id = ag.agent_asset "
              + "WHERE a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "'"
              + "GROUP BY a.asset_id"
              + ")"
              + "SELECT CONCAT(a.asset_id, '_', i.inject_exercise) as base_id, "
              + "a.asset_id as vulnerable_endpoint_id, "
              + "i.inject_exercise as vulnerable_endpoint_simulation, "
              + "MAX(se.scenario_id) as vulnerable_endpoint_scenario, " // MAX here is used to get 1
              // element and not a list
              // because we know that 1
              // exercise is linked to
              // only 1 scenario
              + "a.endpoint_hostname as vulnerable_endpoint_hostname, "
              + "a.endpoint_platform as vulnerable_endpoint_platform, "
              + "a.endpoint_is_eol as vulnerable_endpoint_eol, "
              + "a.endpoint_arch as vulnerable_endpoint_architecture, "
              + "e.exercise_created_at as vulnerable_endpoint_created_at, "
              + "CASE WHEN e.exercise_updated_at > a.asset_updated_at "
              + "  THEN e.exercise_updated_at ELSE a.asset_updated_at END as vulnerable_endpoint_updated_at, "
              + "array_agg(fa.finding_id) FILTER ( WHERE fa.finding_id IS NOT NULL ) as vulnerable_endpoint_findings, "
              + "array_agg(distinct at.tag_id) FILTER ( WHERE at.tag_id IS NOT NULL ) as vulnerable_endpoint_tags, "
              + "ag.agent_ids as vulnerable_endpoint_agents, "

              // denormalised
              + "array_agg(f.finding_id) FILTER ( WHERE f.finding_id IS NOT NULL AND f.finding_type = 'CVE' ) as vulnerable_endpoint_cves, "
              + "ag.agent_last_seen as vulnerable_endpoint_agents_last_seen, "
              + "ag.agent_privs as vulnerable_endpoint_agents_privileges "
              + "FROM findings f "
              + "JOIN findings_assets fa ON f.finding_id = fa.finding_id "
              + "JOIN assets a ON a.asset_id = fa.asset_id "
              + "LEFT JOIN agents_per_asset ag ON a.asset_id = ag.asset_id "
              + "LEFT JOIN assets_tags at ON a.asset_id = at.asset_id "
              + "JOIN injects i ON i.inject_id = f.finding_inject_id "
              + "JOIN exercises e ON i.inject_exercise = e.exercise_id "
              + "LEFT JOIN scenarios_exercises se ON se.exercise_id = e.exercise_id "
              + "WHERE (e.exercise_updated_at > :from OR a.asset_updated_at > :from) "
              + "AND f.finding_type = 'CVE' "
              + "AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' "
              + "GROUP BY a.asset_id, i.inject_exercise, e.exercise_updated_at, e.exercise_created_at, ag.agent_ids, ag.agent_last_seen, ag.agent_privs "
              + "ORDER BY e.exercise_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawVulnerableEndpoint> findForIndexing(@Param("from") Instant from);
}
