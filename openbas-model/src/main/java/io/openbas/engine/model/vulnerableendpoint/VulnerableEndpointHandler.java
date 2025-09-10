package io.openbas.engine.model.vulnerableendpoint;

import static io.openbas.engine.EsUtils.buildRestrictions;

import io.openbas.database.model.Finding;
import io.openbas.database.raw.RawVulnerableEndpoint;
import io.openbas.database.repository.FindingRepository;
import io.openbas.database.repository.VulnerableEndpointRepository;
import io.openbas.engine.Handler;
import io.openbas.helper.AgentHelper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VulnerableEndpointHandler implements Handler<EsVulnerableEndpoint> {

  private final VulnerableEndpointRepository vulnerableEndpointRepository;
  private final FindingRepository findingRepository;

  @Override
  public List<EsVulnerableEndpoint> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawVulnerableEndpoint> forIndexing =
        this.vulnerableEndpointRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            endpoint -> {
              EsVulnerableEndpoint esVulnerableEndpoint = new EsVulnerableEndpoint();
              // Base
              esVulnerableEndpoint.setBase_id(endpoint.getBase_id());
              esVulnerableEndpoint.setBase_representative(
                  endpoint.getVulnerable_endpoint_hostname());
              esVulnerableEndpoint.setBase_created_at(endpoint.getVulnerable_endpoint_created_at());
              esVulnerableEndpoint.setBase_updated_at(endpoint.getVulnerable_endpoint_updated_at());
              // not sure what to put here, if anything
              esVulnerableEndpoint.setBase_restrictions(
                  buildRestrictions(endpoint.getVulnerable_endpoint_id()));

              esVulnerableEndpoint.setVulnerable_endpoint_id(endpoint.getVulnerable_endpoint_id());
              esVulnerableEndpoint.setVulnerable_endpoint_platform(
                  endpoint.getVulnerable_endpoint_platform());
              esVulnerableEndpoint.setVulnerable_endpoint_hostname(
                  endpoint.getVulnerable_endpoint_hostname());
              esVulnerableEndpoint.setVulnerable_endpoint_architecture(
                  endpoint.getVulnerable_endpoint_architecture());

              // denormalisation
              // agents privs
              esVulnerableEndpoint.setVulnerable_endpoint_agents_privileges(
                  endpoint.getVulnerable_endpoint_agents_privileges());

              // endpoint status
              if (endpoint.getVulnerable_endpoint_agents_last_seen() == null
                  || endpoint.getVulnerable_endpoint_agents_last_seen().isEmpty()) {
                esVulnerableEndpoint.setVulnerable_endpoint_agents_active_status(List.of());
              } else {
                AgentHelper agentHelper = new AgentHelper();
                esVulnerableEndpoint.setVulnerable_endpoint_agents_active_status(
                    endpoint.getVulnerable_endpoint_agents_last_seen().stream()
                        .map(
                            status ->
                                agentHelper.isAgentActiveFromLastSeen(
                                    /*
                                      timestamp in database has no timezone but was recorded as UTC
                                      first convert to a LocalDateTime (which has no timezone) and
                                      then assign a UTC timezone without changing the numbers.
                                      The .toInstant() method wasn't suitable as it assumed the numbers
                                      were of the local timezone and was doing an offset to convert to UTC.
                                    */
                                    status.toLocalDateTime().toInstant(ZoneOffset.UTC)))
                        .toList());
              }

              // update/replace flag
              if (endpoint.getVulnerable_endpoint_cves() == null
                  || endpoint.getVulnerable_endpoint_cves().isEmpty()) {
                esVulnerableEndpoint.setVulnerable_endpoint_action(
                    VulnerableEndpointAction.OK.name());
              } else {
                if (endpoint.getVulnerable_endpoint_eol()) {
                  esVulnerableEndpoint.setVulnerable_endpoint_action(
                      VulnerableEndpointAction.REPLACE.name());
                } else {
                  esVulnerableEndpoint.setVulnerable_endpoint_action(
                      VulnerableEndpointAction.UPDATE.name());
                }
              }

              // finding types summary
              List<Finding> findings =
                  StreamSupport.stream(
                          findingRepository
                              .findAllById(endpoint.getVulnerable_endpoint_findings())
                              .spliterator(),
                          false)
                      .toList();
              HashMap<String, List<Finding>> byCategory = new HashMap<>();
              for (Finding finding : findings) {
                byCategory
                    .computeIfAbsent(finding.getType().name(), s -> new ArrayList<>())
                    .add(finding);
              }
              List<String> summaryItems =
                  byCategory.keySet().stream()
                      .map(k -> String.format("%d %s", byCategory.get(k).size(), k))
                      .toList();
              esVulnerableEndpoint.setVulnerable_endpoint_findings_summary(
                  String.join(", ", summaryItems));

              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (endpoint.getVulnerable_endpoint_findings() != null
                  && !endpoint.getVulnerable_endpoint_findings().isEmpty()) {
                dependencies.addAll(endpoint.getVulnerable_endpoint_findings());
                esVulnerableEndpoint.setBase_findings_side(
                    endpoint.getVulnerable_endpoint_findings());
              } else {
                esVulnerableEndpoint.setBase_findings_side(Set.of());
              }
              if (endpoint.getVulnerable_endpoint_tags() != null
                  && !endpoint.getVulnerable_endpoint_tags().isEmpty()) {
                esVulnerableEndpoint.setBase_tags_side(endpoint.getVulnerable_endpoint_tags());
              } else {
                esVulnerableEndpoint.setBase_tags_side(Set.of());
              }
              if (endpoint.getVulnerable_endpoint_agents() != null
                  && !endpoint.getVulnerable_endpoint_agents().isEmpty()) {
                esVulnerableEndpoint.setBase_agents_side(endpoint.getVulnerable_endpoint_agents());
              } else {
                esVulnerableEndpoint.setBase_agents_side(Set.of());
              }
              if (endpoint.getVulnerable_endpoint_simulation() != null) {
                dependencies.add(endpoint.getVulnerable_endpoint_simulation());
                esVulnerableEndpoint.setBase_simulation_side(
                    endpoint.getVulnerable_endpoint_simulation());
              } else {
                esVulnerableEndpoint.setBase_simulation_side(null);
              }
              if (endpoint.getVulnerable_endpoint_scenario() != null) {
                dependencies.add(endpoint.getVulnerable_endpoint_scenario());
                esVulnerableEndpoint.setBase_scenario_side(
                    endpoint.getVulnerable_endpoint_scenario());
              } else {
                esVulnerableEndpoint.setBase_scenario_side(null);
              }
              esVulnerableEndpoint.setBase_dependencies(dependencies);
              return esVulnerableEndpoint;
            })
        .toList();
  }
}
