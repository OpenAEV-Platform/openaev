package io.openbas.database.helper;

import io.openbas.database.model.*;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class InjectStatusRepositoryHelper {

  private final NamedParameterJdbcTemplate jt;

  public void updateInjectStatusWithTraces(
      List<SimpleInjectStatus> injectStatusList, List<SimpleExecutionTrace> executionTraceList) {
    if (!executionTraceList.isEmpty()) {
      String insertExecutionTraceSQL =
          """
                    INSERT INTO execution_traces(
                             execution_action,
                             execution_agent_id,
                             execution_context_identifiers,
                             execution_inject_status_id,
                             execution_inject_test_status_id,
                             execution_message,
                             execution_status,
                             execution_time,
                             execution_trace_id)
                          VALUES (
                             :execution_action,
                             :execution_agent_id,
                             string_to_array(:execution_context_identifiers, ','),
                             :execution_inject_status_id,
                             :execution_inject_test_status_id,
                             :execution_message,
                             :execution_status,
                             :execution_time,
                             :execution_trace_id
                          )
              """;

      MapSqlParameterSource[] params =
          executionTraceList.stream()
              .map(
                  r -> {
                    MapSqlParameterSource paramValues = new MapSqlParameterSource();
                    paramValues.addValue("execution_trace_id", UUID.randomUUID().toString());
                    paramValues.addValue("execution_action", r.getAction().name());
                    paramValues.addValue("execution_agent_id", r.getAgentId());
                    paramValues.addValue(
                        "execution_context_identifiers", String.join(",", r.getIdentifiers()));
                    paramValues.addValue("execution_inject_status_id", r.getInjectStatusId());
                    paramValues.addValue(
                        "execution_inject_test_status_id", r.getInjectTestStatusId());
                    paramValues.addValue("execution_message", r.getMessage());
                    paramValues.addValue("execution_status", r.getStatus().name());
                    paramValues.addValue(
                        "execution_time", Timestamp.from(r.getTime()), Types.TIMESTAMP);
                    return paramValues;
                  })
              .toArray(MapSqlParameterSource[]::new);

      jt.batchUpdate(insertExecutionTraceSQL, params);
    }

    if (!injectStatusList.isEmpty()) {
      Map<ExecutionStatus, List<SimpleInjectStatus>> mapInjectStatus =
          injectStatusList.stream().collect(Collectors.groupingBy(SimpleInjectStatus::getName));

      String updateInjectStatusSQL =
          """
                        UPDATE injects_statuses SET
                                tracking_end_date = NOW(),
                                status_name = :current_status_name
                              WHERE status_id IN (:status_ids)

                  """;
      for (Map.Entry<ExecutionStatus, List<SimpleInjectStatus>> executionTrace :
          mapInjectStatus.entrySet()) {
        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue("current_status_name", executionTrace.getKey().name());
        param.addValue(
            "status_ids",
            new ArrayList<>(
                executionTrace.getValue().stream().map(SimpleInjectStatus::getId).toList()));
        try {
          jt.update(updateInjectStatusSQL, param);
        } catch (Exception e) {
          log.error("Error during update : {}, {}", updateInjectStatusSQL, param.getValues(), e);
          throw e;
        }
      }

      String updateInjectUpdateDate =
          """
            UPDATE injects SET inject_updated_at = NOW()
              WHERE inject_id IN (:inject_ids)
          """;

      MapSqlParameterSource param = new MapSqlParameterSource();
      param.addValue(
          "inject_ids",
          injectStatusList.stream().map(SimpleInjectStatus::getInjectId).distinct().toList());

      jt.update(updateInjectUpdateDate, param);
    }
  }

  public Map<String, SimpleInjectStatus> getSimpleInjectStatusesByInjectId(List<String> injectIds) {
    if (!injectIds.isEmpty()) {
      String query = "SELECT * FROM injects_statuses WHERE status_inject IN (:inject_ids)";

      MapSqlParameterSource paramValues = new MapSqlParameterSource();
      paramValues.addValue("inject_ids", injectIds);

      return jt.queryForStream(
              query, paramValues, new SimpleInjectStatus.SimpleInjectStatusRowMapper())
          .collect(Collectors.toMap(SimpleInjectStatus::getInjectId, Function.identity()));
    }
    return new HashMap<>();
  }

  public Map<String, List<SimpleExecutionTrace>> getSimpleExecutionTracesByInjectStatusId(
      List<String> injectIds) {
    if (!injectIds.isEmpty()) {
      String query =
          "SELECT * FROM execution_traces WHERE execution_traces.execution_inject_status_id IN (:inject_ids)";

      MapSqlParameterSource paramValues = new MapSqlParameterSource();
      paramValues.addValue("inject_ids", injectIds);

      return jt.queryForStream(
              query, paramValues, new SimpleExecutionTrace.SimpleExecutionTraceRowMapper())
          .collect(
              Collectors.groupingBy(SimpleExecutionTrace::getInjectStatusId, Collectors.toList()));
    }
    return new HashMap<>();
  }

  public Map<String, Integer> getAssetsNumberByInjectId(List<String> injectIds) {
    if (!injectIds.isEmpty()) {
      String query =
          "SELECT ia.inject_id, COUNT(ag.agent_id) as number "
              + "FROM injects_assets ia "
              + "LEFT JOIN assets ON ia.asset_id = assets.asset_id "
              + "LEFT JOIN agents ag ON ag.agent_asset = ia.asset_id "
              + "WHERE ia.inject_id IN (:inject_ids) "
              + "AND assets.asset_type = 'Endpoint' "
              + "GROUP BY ia.inject_id";

      MapSqlParameterSource paramValues = new MapSqlParameterSource();
      paramValues.addValue("inject_ids", injectIds);

      Map<String, Integer> resultsByInjectId = new HashMap<>();
      jt.queryForList(query, paramValues)
          .forEach(
              stringObjectMap -> {
                String key = stringObjectMap.get("inject_id").toString();
                Integer value = Math.toIntExact((Long) stringObjectMap.get("number"));
                resultsByInjectId.putIfAbsent(key, 0);
                resultsByInjectId.put(key, value);
              });
      return resultsByInjectId;
    }
    return new HashMap<>();
  }

  public void saveFindings(Set<SimpleFinding> findingToSave) {
    if (!findingToSave.isEmpty()) {
      // First of all, we check for the unicity of the finding
      String query = "SELECT * FROM findings WHERE findings.finding_inject_id IN (:inject_ids)";

      MapSqlParameterSource searchParams = new MapSqlParameterSource();
      searchParams.addValue(
          "inject_ids", findingToSave.stream().map(SimpleFinding::getInjectId).distinct().toList());

      Map<String, List<SimpleFinding>> mapSimpleFindingByInjectId =
          jt.queryForStream(query, searchParams, new SimpleFinding.SimpleFindingRowMapper())
              .collect(Collectors.groupingBy(SimpleFinding::getInjectId));

      // Doing a reconciliation
      Set<String> alreadyExistingId = new HashSet<>();
      for (SimpleFinding finding : findingToSave) {
        if (mapSimpleFindingByInjectId.get(finding.getInjectId()) != null) {
          Optional<SimpleFinding> alreadyExistingFinding =
              mapSimpleFindingByInjectId.get(finding.getInjectId()).stream()
                  .filter(
                      simpleFinding ->
                          simpleFinding.getField().equals(finding.getField())
                              && simpleFinding.getType().equals(finding.getType())
                              && simpleFinding.getValue().equals(finding.getValue()))
                  .findAny();

          alreadyExistingFinding.ifPresent(
              simpleFinding -> {
                finding.setId(simpleFinding.getId());
                alreadyExistingId.add(simpleFinding.getId());
              });
        }
      }

      String insertExecutionTraceSQL =
          """
            INSERT INTO findings(
                   finding_id,
                   finding_field,
                   finding_type,
                   finding_value,
                   finding_labels,
                   finding_inject_id,
                   finding_created_at,
                   finding_updated_at,
                   finding_name)
                VALUES (
                    :finding_id,
                    :finding_field,
                    :finding_type,
                    :finding_value,
                    string_to_array(:finding_labels, ','),
                    :finding_inject_id,
                    :finding_created_at,
                    :finding_updated_at,
                    :finding_name
                )
        """;

      Set<SimpleFinding> newFindings =
          new HashSet<>(
              findingToSave.stream()
                  .filter(simpleFinding -> simpleFinding.getId() == null)
                  .toList());

      for (SimpleFinding finding : newFindings) {
        finding.setId(UUID.randomUUID().toString());
      }

      MapSqlParameterSource[] params =
          newFindings.stream()
              .map(
                  r -> {
                    MapSqlParameterSource paramValues = new MapSqlParameterSource();
                    paramValues.addValue("finding_id", r.getId());
                    paramValues.addValue("finding_field", r.getField());
                    paramValues.addValue("finding_type", r.getType());
                    paramValues.addValue("finding_value", r.getValue());
                    paramValues.addValue("finding_labels", String.join(",", r.getLabels()));
                    paramValues.addValue("finding_inject_id", r.getInjectId());
                    paramValues.addValue(
                        "finding_created_at", Timestamp.from(r.getCreationDate()), Types.TIMESTAMP);
                    paramValues.addValue(
                        "finding_updated_at", Timestamp.from(r.getUpdateDate()), Types.TIMESTAMP);
                    paramValues.addValue("finding_name", r.getName());
                    return paramValues;
                  })
              .toArray(MapSqlParameterSource[]::new);

      jt.batchUpdate(insertExecutionTraceSQL, params);

      if (alreadyExistingId.stream().filter(Objects::nonNull).distinct().findAny().isPresent()) {

        String updateFindingUpdateDate =
            """
            UPDATE findings SET
              finding_updated_at = NOW()
              WHERE finding_id IN (:finding_ids)
          """;

        MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue(
            "finding_ids", alreadyExistingId.stream().filter(Objects::nonNull).distinct().toList());

        jt.update(updateFindingUpdateDate, param);
      }

      updateAssetLinks(findingToSave);
      updateTeamLinks(findingToSave);
      updateUserLinks(findingToSave);
    }
  }

  private void updateAssetLinks(Set<SimpleFinding> findingToSave) {
    if (!findingToSave.isEmpty()) {
      String query = "SELECT * FROM findings_assets WHERE finding_id IN (:finding_ids)";

      MapSqlParameterSource searchParams = new MapSqlParameterSource();
      searchParams.addValue(
          "finding_ids", findingToSave.stream().map(SimpleFinding::getId).distinct().toList());

      Map<String, List<String>> mapAssetIdByFindingId = new HashMap<>();
      jt.queryForList(query, searchParams)
          .forEach(
              stringObjectMap -> {
                String key = stringObjectMap.get("finding_id").toString();
                String value = stringObjectMap.get("asset_id").toString();
                mapAssetIdByFindingId.computeIfAbsent(key, k -> new ArrayList<>());
                mapAssetIdByFindingId.get(key).add(value);
              });

      findingToSave.forEach(
          simpleFinding -> {
            if (mapAssetIdByFindingId.get(simpleFinding.getId()) != null) {
              simpleFinding.getAssets().removeAll(mapAssetIdByFindingId.get(simpleFinding.getId()));
            }
          });

      String insertAssetsLinks =
          """
            INSERT INTO findings_assets(
                   finding_id,
                   asset_id)
                VALUES (
                    :finding_id,
                    :asset_id
                )
        """;

      MapSqlParameterSource[] paramsUpdateAssets =
          findingToSave.stream()
              .flatMap(
                  r ->
                      r.getAssets().stream()
                          .distinct()
                          .map(
                              s -> {
                                MapSqlParameterSource paramValues = new MapSqlParameterSource();
                                paramValues.addValue("finding_id", r.getId());
                                paramValues.addValue("asset_id", s);
                                return paramValues;
                              }))
              .toArray(MapSqlParameterSource[]::new);
      jt.batchUpdate(insertAssetsLinks, paramsUpdateAssets);
    }
  }

  private void updateTeamLinks(Set<SimpleFinding> findingToSave) {
    if (!findingToSave.isEmpty()) {
      String query = "SELECT * FROM findings_teams WHERE finding_id IN (:finding_ids)";

      MapSqlParameterSource searchParams = new MapSqlParameterSource();
      searchParams.addValue(
          "finding_ids", findingToSave.stream().map(SimpleFinding::getId).distinct().toList());

      Map<String, List<String>> mapTeamIdByFindingId = new HashMap<>();
      jt.queryForList(query, searchParams)
          .forEach(
              stringObjectMap -> {
                String key = stringObjectMap.get("finding_id").toString();
                String value = stringObjectMap.get("team_id").toString();
                mapTeamIdByFindingId.computeIfAbsent(key, k -> new ArrayList<>());
                mapTeamIdByFindingId.get(key).add(value);
              });

      findingToSave.forEach(
          simpleFinding -> {
            if (mapTeamIdByFindingId.get(simpleFinding.getId()) != null) {
              simpleFinding.getTeams().removeAll(mapTeamIdByFindingId.get(simpleFinding.getId()));
            }
          });
      String insertTeamsLinks =
          """
                    INSERT INTO findings_teams(
                           finding_id,
                           team_id)
                        VALUES (
                            :finding_id,
                            :team_id
                        )
                """;

      MapSqlParameterSource[] paramsUpdateTeams =
          findingToSave.stream()
              .flatMap(
                  r ->
                      r.getTeams().stream()
                          .distinct()
                          .map(
                              s -> {
                                MapSqlParameterSource paramValues = new MapSqlParameterSource();
                                paramValues.addValue("finding_id", r.getId());
                                paramValues.addValue("team_id", s);
                                return paramValues;
                              }))
              .toArray(MapSqlParameterSource[]::new);
      jt.batchUpdate(insertTeamsLinks, paramsUpdateTeams);
    }
  }

  private void updateUserLinks(Set<SimpleFinding> findingToSave) {
    if (!findingToSave.isEmpty()) {
      String query = "SELECT * FROM findings_users WHERE finding_id IN (:finding_ids)";

      MapSqlParameterSource searchParams = new MapSqlParameterSource();
      searchParams.addValue(
          "finding_ids", findingToSave.stream().map(SimpleFinding::getId).distinct().toList());

      Map<String, List<String>> mapUserIdByFindingId = new HashMap<>();
      jt.queryForList(query, searchParams)
          .forEach(
              stringObjectMap -> {
                String key = stringObjectMap.get("finding_id").toString();
                String value = stringObjectMap.get("user_id").toString();
                mapUserIdByFindingId.computeIfAbsent(key, k -> new ArrayList<>());
                mapUserIdByFindingId.get(key).add(value);
              });

      findingToSave.forEach(
          simpleFinding -> {
            if (mapUserIdByFindingId.get(simpleFinding.getId()) != null) {
              simpleFinding.getUsers().removeAll(mapUserIdByFindingId.get(simpleFinding.getId()));
            }
          });
      String insertUsersLinks =
          """
                    INSERT INTO findings_users(
                           finding_id,
                           user_id)
                        VALUES (
                            :finding_id,
                            :user_id
                        )
                """;

      MapSqlParameterSource[] paramsUpdateUsers =
          findingToSave.stream()
              .flatMap(
                  r ->
                      r.getUsers().stream()
                          .distinct()
                          .map(
                              s -> {
                                MapSqlParameterSource paramValues = new MapSqlParameterSource();
                                paramValues.addValue("finding_id", r.getId());
                                paramValues.addValue("user_id", s);
                                return paramValues;
                              }))
              .toArray(MapSqlParameterSource[]::new);
      jt.batchUpdate(insertUsersLinks, paramsUpdateUsers);
    }
  }
}
