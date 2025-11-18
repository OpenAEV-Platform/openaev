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
import io.openaev.utils.TimeUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
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
    List<SentinelOneAgent> sentinelOneAgents = this.client.agents();
    Map<String, List<SentinelOneAgent>> siteAgentMap = new HashMap<>();
    Map<String, List<SentinelOneAgent>> accountAgentMap = new HashMap<>();
    Map<String, List<SentinelOneAgent>> groupAgentMap = new HashMap<>();
    if (!sentinelOneAgents.isEmpty()) {
      // Create map for each asset group
      for (SentinelOneAgent agent : sentinelOneAgents) {
        siteAgentMap.computeIfAbsent(agent.getSiteId(), k -> new ArrayList<>()).add(agent);
        accountAgentMap.computeIfAbsent(agent.getAccountId(), k -> new ArrayList<>()).add(agent);
        groupAgentMap.computeIfAbsent(agent.getGroupId(), k -> new ArrayList<>()).add(agent);
      }
      // Manage agents for site
      for (String siteId : siteAgentMap.keySet()) {
        Optional<AssetGroup> existingAssetGroup;
        existingAssetGroup = assetGroupService.findByExternalReference(siteId);
        AssetGroup assetGroup;
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(siteId);
        }
        List<SentinelOneAgent> agentsForSite = siteAgentMap.get(siteId);
        String siteName = agentsForSite.getFirst().getSiteName();
        assetGroup.setName(siteName);
        log.debug(
            "SentinelOne executor provisioning based on "
                + agentsForSite.size()
                + " agents for the site "
                + siteName);
        List<Agent> agents =
            endpointService.syncAgentsEndpoints(
                toAgentEndpoint(agentsForSite),
                agentService.getAgentsByExecutorType(SENTINELONE_EXECUTOR_TYPE));
        assetGroup.setAssets(agents.stream().map(Agent::getAsset).toList());
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
        // Manage agents for account
        for (String accountId : accountAgentMap.keySet()) {
          List<SentinelOneAgent> agentsForAccount =
              agentsForSite.stream()
                  .filter(agent -> accountId.equals(agent.getAccountId()))
                  .toList();
          existingAssetGroup = assetGroupService.findByExternalReference(accountId);
          if (existingAssetGroup.isPresent()) {
            assetGroup = existingAssetGroup.get();
          } else {
            assetGroup = new AssetGroup();
            assetGroup.setExternalReference(accountId);
          }
          String accountName = siteName + "_" + agentsForAccount.getFirst().getAccountName();
          assetGroup.setName(accountName);
          List<String> agentsIdsForAccount =
              agentsForAccount.stream().map(SentinelOneAgent::getUuid).toList();
          assetGroup.setAssets(
              agents.stream()
                  .filter(agent -> agentsIdsForAccount.contains(agent.getId()))
                  .map(Agent::getAsset)
                  .toList());
          assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
          // Manage agents for group
          for (String groupId : groupAgentMap.keySet()) {
            List<SentinelOneAgent> agentsForGroup =
                agentsForAccount.stream()
                    .filter(agent -> groupId.equals(agent.getGroupId()))
                    .toList();
            existingAssetGroup = assetGroupService.findByExternalReference(groupId);
            if (existingAssetGroup.isPresent()) {
              assetGroup = existingAssetGroup.get();
            } else {
              assetGroup = new AssetGroup();
              assetGroup.setExternalReference(groupId);
            }
            String groupName = accountName + "_" + agentsForGroup.getFirst().getGroupName();
            assetGroup.setName(groupName);
            List<String> agentsIdsForGroup =
                agentsForGroup.stream().map(SentinelOneAgent::getUuid).toList();
            assetGroup.setAssets(
                agents.stream()
                    .filter(agent -> agentsIdsForGroup.contains(agent.getId()))
                    .map(Agent::getAsset)
                    .toList());
            assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
          }
        }
      }
    }
  }

  private List<AgentRegisterInput> toAgentEndpoint(List<SentinelOneAgent> agents) {
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
              input.setLastSeen(TimeUtils.toInstant(sentinelOneAgent.getLastActiveDate()));
              return input;
            })
        .toList();
  }
}
