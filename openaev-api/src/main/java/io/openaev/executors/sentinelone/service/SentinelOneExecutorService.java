package io.openaev.executors.sentinelone.service;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.*;
import io.openaev.executors.DeviceSync;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.model.DeviceGroup;
import io.openaev.executors.model.RemoteDevice;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import io.openaev.executors.sentinelone.model.SentinelOneNetwork;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SentinelOneExecutorService implements Runnable {
  private final SentinelOneExecutorClient client;
  private final DeviceSync deviceSync;
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
    this.deviceSync = new DeviceSync(endpointService, agentService, assetGroupService, tenantTx);
  }

  @Override
  public void run() {
    deviceSync.sync(executor, this::devices);
  }

  private List<RemoteDevice> devices() {
    log.info("Running SentinelOne executor endpoints gathering...");
    return this.client.agents().stream()
        .map(agent -> new RemoteDevice(toAgentEndpoint(agent), groupsOf(agent)))
        .toList();
  }

  private static Set<DeviceGroup> groupsOf(SentinelOneAgent agent) {
    String accountName = agent.getAccountName();
    String siteName = accountName + "_" + agent.getSiteName();
    return new LinkedHashSet<>(
        List.of(
            new DeviceGroup(agent.getAccountId(), accountName, null),
            new DeviceGroup(agent.getSiteId(), siteName, null),
            new DeviceGroup(agent.getGroupId(), siteName + "_" + agent.getGroupName(), null)));
  }

  private AgentRegisterInput toAgentEndpoint(SentinelOneAgent sentinelOneAgent) {
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
  }

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
