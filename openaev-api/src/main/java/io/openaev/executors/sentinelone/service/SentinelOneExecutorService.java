package io.openaev.executors.sentinelone.service;

import io.openaev.database.model.*;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import io.openaev.executors.sentinelone.model.SentinelOneNetwork;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.sentinelone", name = "enable")
@Slf4j
@Service
public class SentinelOneExecutorService implements Runnable {

  public static final String SENTINELONE_EXECUTOR_TYPE = "openaev_sentinelone";
  public static final String SENTINELONE_EXECUTOR_NAME = "SentinelOne";
  private static final String SENTINELONE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#sentinelone-agent";

  private static final String SENTINELONE_EXECUTOR_BACKGROUND_COLOR = "#6001FC";

  private final SentinelOneExecutorClient client;
  private final SentinelOneExecutorConfig config;
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

  @Autowired
  public SentinelOneExecutorService(
      ExecutorService executorService,
      SentinelOneExecutorClient client,
      SentinelOneExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    try {
      if (config.isEnable()) {
        this.executor =
            executorService.register(
                config.getId(),
                SENTINELONE_EXECUTOR_TYPE,
                SENTINELONE_EXECUTOR_NAME,
                SENTINELONE_EXECUTOR_DOCUMENTATION_LINK,
                SENTINELONE_EXECUTOR_BACKGROUND_COLOR,
                getClass().getResourceAsStream("/img/icon-sentinelone.png"),
                getClass().getResourceAsStream("/img/banner-sentinelone.png"),
                new String[] {
                  Endpoint.PLATFORM_TYPE.Windows.name(),
                  Endpoint.PLATFORM_TYPE.Linux.name(),
                  Endpoint.PLATFORM_TYPE.MacOS.name()
                });
      } else {
        if (executor != null) {
          executorService.remove(config.getId());
        }
      }
    } catch (Exception e) {
      log.error(String.format("Error creating SentinelOne executor: %s", e.getMessage()), e);
    }
  }

  @Override
  public void run() {
    log.info("Running SentinelOne executor endpoints gathering...");
    Set<SentinelOneAgent> sentinelOneAgents = this.client.agents();
    if (!sentinelOneAgents.isEmpty()) {
      // Put sentinel one agents into two maps: account/site/group id with agents ids +
      // account/site/group id with account/site/group name
      Map<String, List<String>> assetGroupIdAgentIdsMap = new HashMap<>();
      Map<String, String> assetGroupIdNameMap = new HashMap<>();
      for (SentinelOneAgent agent : sentinelOneAgents) {
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
              toAgentEndpoint(sentinelOneAgents),
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

  private List<AgentRegisterInput> toAgentEndpoint(Set<SentinelOneAgent> agents) {
    return agents.stream()
        .map(
            sentinelOneAgent -> {
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(executor);
              input.setExternalReference(sentinelOneAgent.getUuid());
              input.setElevated(true);
              input.setService(true);
              input.setName(sentinelOneAgent.getComputerName());
              input.setSeenIp(sentinelOneAgent.getExternalIp());
              input.setIps(
                  sentinelOneAgent.getNetworkInterfaces().stream()
                      .flatMap(network -> network.getInet().stream())
                      .distinct()
                      .toList()
                      .toArray(new String[0]));
              input.setMacAddresses(
                  sentinelOneAgent.getNetworkInterfaces().stream()
                      .map(SentinelOneNetwork::getPhysical)
                      .distinct()
                      .toList()
                      .toArray(new String[0]));
              input.setHostname(sentinelOneAgent.getComputerName());
              input.setPlatform(toPlatform(sentinelOneAgent.getOsType()));
              input.setArch(toArch(sentinelOneAgent.getOsArch()));
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(Instant.parse(sentinelOneAgent.getLastActiveDate()));
              return input;
            })
        .collect(Collectors.toList());
  }
}
