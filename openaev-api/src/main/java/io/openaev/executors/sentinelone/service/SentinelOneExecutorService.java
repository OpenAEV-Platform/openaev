package io.openaev.executors.sentinelone.service;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
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

@Slf4j
public class SentinelOneExecutorService implements Runnable {
  private final SentinelOneExecutorClient client;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;
  private Executor executor;

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

  public SentinelOneExecutorService(
      Executor executor,
      SentinelOneExecutorClient client,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      TenantScopedTransaction tenantTx) {
    this.executor = executor;
    this.client = client;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.tenantTx = tenantTx;
  }

  @Override
  public void run() {
    try {
      tenantTx.execute(
          TxCtx.forTenant(executor.getTenantId()),
          () -> {
            // Bridge for v1 tables (Tag, Asset, Agent, AssetGroup) still relying on
            // TenantContext via HibernateFilterTransactionAspect: this Runnable executes on the
            // shared scheduler thread pool outside any HTTP request, so TenantContext is never
            // set here otherwise and falls back to the default tenant, silently scoping the v1
            // Hibernate filter to the wrong tenant.
            TenantContext.setCurrentTenant(executor.getTenantId());
            doRun();
            return null;
          });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void doRun() {
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
              agentService.getAgentsByExecutorIdAndTenantId(
                  executor.getId(), executor.getTenantId()),
              executor.getTenantId());
      // For each sentinel one account/site/group id, create/update the relevant OpenAEV asset group
      Optional<AssetGroup> existingAssetGroup;
      AssetGroup assetGroup;
      for (Map.Entry<String, List<String>> assetGroupIdAgentIds :
          assetGroupIdAgentIdsMap.entrySet()) {
        String assetGroupId = assetGroupIdAgentIds.getKey();
        List<String> agentIds = assetGroupIdAgentIds.getValue();
        existingAssetGroup =
            assetGroupService.findByExternalReference(assetGroupId, executor.getTenantId());
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(assetGroupId);
          assetGroup.setTenant(new Tenant(executor.getTenantId()));
        }
        assetGroup.setName(assetGroupIdNameMap.get(assetGroupId));
        assetGroup.setAssets(
            agents.stream()
                .filter(agent -> agentIds.contains(agent.getId()))
                .map(Agent::getAsset)
                .collect(Collectors.toCollection(ArrayList::new)));
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

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
