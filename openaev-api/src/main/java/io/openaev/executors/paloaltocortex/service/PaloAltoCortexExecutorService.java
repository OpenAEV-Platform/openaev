package io.openaev.executors.paloaltocortex.service;

import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexEndpoint;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexNetwork;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaloAltoCortexExecutorService implements Runnable {
  private final PaloAltoCortexExecutorClient client;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform.toLowerCase()) {
      case "linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "macos" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotBlank final String arch) {
    return switch (arch.toLowerCase()) {
      case "64 bit" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> Endpoint.PLATFORM_ARCH.Unknown;
    };
  }

  public PaloAltoCortexExecutorService(
      Executor executor,
      PaloAltoCortexExecutorClient client,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.executor = executor;
    this.client = client;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
  }

  @Override
  public void run() {
    log.info("Running SentinelOne executor endpoints gathering...");
    Set<PaloAltoCortexEndpoint> paloAltoCortexEndpoints = this.client.agents();
    if (!paloAltoCortexEndpoints.isEmpty()) {
      // Put sentinel one agents into two maps: account/site/group id with agents ids +
      // account/site/group id with account/site/group name
      Map<String, List<String>> assetGroupIdAgentIdsMap = new HashMap<>();
      Map<String, String> assetGroupIdNameMap = new HashMap<>();
      for (PaloAltoCortexEndpoint agent : paloAltoCortexEndpoints) {
        String accountName = agent.getAccountName();
        String siteName = accountName + "_" + agent.getSiteName();
        String groupName = siteName + "_" + agent.getGroupName();
        String agentId = agent.getUuid();
        assetGroupIdAgentIdsMap
            .computeIfAbsent(agent.getAccountId(), k -> new ArrayList<>())
            .add(agentId);
        assetGroupIdNameMap.putIfAbsent(agent.getAccountId(), accountName);
        assetGroupIdAgentIdsMap
            .computeIfAbsent(agent.getSiteId(), k -> new ArrayList<>())
            .add(agentId);
        assetGroupIdNameMap.putIfAbsent(agent.getSiteId(), siteName);
        assetGroupIdAgentIdsMap
            .computeIfAbsent(agent.getGroupId(), k -> new ArrayList<>())
            .add(agentId);
        assetGroupIdNameMap.putIfAbsent(agent.getGroupId(), groupName);
      }
      // Sync all sentinel one agents to become OpenAEV agents/endpoints
      List<Agent> agents =
          endpointService.syncAgentsEndpoints(
              toAgentEndpoint(paloAltoCortexEndpoints),
              agentService.getAgentsByExecutorType(SENTINELONE_EXECUTOR_TYPE));
      // For each sentinel one account/site/group id, create/update the relevant OpenAEV asset group
      Optional<AssetGroup> existingAssetGroup;
      AssetGroup assetGroup;
      for (Map.Entry<String, List<String>> assetGroupIdAgentIds :
          assetGroupIdAgentIdsMap.entrySet()) {
        String assetGroupId = assetGroupIdAgentIds.getKey();
        List<String> agentIds = assetGroupIdAgentIds.getValue();
        existingAssetGroup = assetGroupService.findByExternalReference(assetGroupId);
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(assetGroupId);
        }
        assetGroup.setName(assetGroupIdNameMap.get(assetGroupId));
        assetGroup.setAssets(
            agents.stream()
                .filter(agent -> agentIds.contains(agent.getId()))
                .map(Agent::getAsset)
                .toList());
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
      }
    }
  }

  private List<AgentRegisterInput> toAgentEndpoint(Set<PaloAltoCortexEndpoint> agents) {
    return agents.stream()
        .map(
            paloAltoCortexEndpoint -> {
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(executor);
              input.setExternalReference(paloAltoCortexEndpoint.getUuid());
              input.setElevated(true);
              input.setService(true);
              input.setName(paloAltoCortexEndpoint.getComputerName());
              input.setSeenIp(paloAltoCortexEndpoint.getExternalIp());
              input.setIps(
                  paloAltoCortexEndpoint.getNetworkInterfaces().stream()
                      .flatMap(network -> network.getInet().stream())
                      .distinct()
                      .toList()
                      .toArray(new String[0]));
              input.setMacAddresses(
                  paloAltoCortexEndpoint.getNetworkInterfaces().stream()
                      .map(PaloAltoCortexNetwork::getPhysical)
                      .distinct()
                      .toList()
                      .toArray(new String[0]));
              input.setHostname(paloAltoCortexEndpoint.getComputerName());
              input.setPlatform(toPlatform(paloAltoCortexEndpoint.getOsType()));
              input.setArch(toArch(paloAltoCortexEndpoint.getOsArch()));
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(Instant.parse(paloAltoCortexEndpoint.getLastActiveDate()));
              return input;
            })
        .collect(Collectors.toList());
  }
}
