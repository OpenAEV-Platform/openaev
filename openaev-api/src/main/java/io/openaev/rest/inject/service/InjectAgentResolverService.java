package io.openaev.rest.inject.service;

import static io.openaev.utils.AgentUtils.isPrimaryAgent;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.service.AssetGroupService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InjectAgentResolverService {

  private final AssetGroupService assetGroupService;

  public List<Agent> getAgentsByInject(Inject inject) {
    List<Agent> agents = new ArrayList<>();
    Set<String> agentIds = new HashSet<>();

    java.util.function.Consumer<Asset> extractAgents =
        asset -> {
          if (!(Hibernate.unproxy(asset) instanceof Endpoint endpoint)) {
            return;
          }
          List<Agent> collectedAgents =
              Optional.ofNullable(endpoint.getAgents()).orElse(Collections.emptyList());
          for (Agent agent : collectedAgents) {
            if (isPrimaryAgent(agent) && !agentIds.contains(agent.getId())) {
              agents.add(agent);
              agentIds.add(agent.getId());
            }
          }
        };

    new ArrayList<>(inject.getAssets()).forEach(extractAgents);
    inject.getAssetGroups().stream()
        .flatMap(assetGroup -> assetGroupService.assetsFromAssetGroup(assetGroup.getId()).stream())
        .forEach(extractAgents);

    return agents;
  }
}
