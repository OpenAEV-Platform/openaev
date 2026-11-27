package io.openaev.executors.paloaltocortex.service;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.*;
import io.openaev.executors.DeviceSync;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.model.DeviceGroup;
import io.openaev.executors.model.RemoteDevice;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexEndpoint;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaloAltoCortexExecutorService implements Runnable {
  private final PaloAltoCortexExecutorClient client;
  private final PaloAltoCortexExecutorConfig config;
  private final DeviceSync deviceSync;
  private Executor executor;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform.toLowerCase()) {
      case "agent_os_linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "agent_os_windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "agent_os_mac" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public PaloAltoCortexExecutorService(
      Executor executor,
      PaloAltoCortexExecutorClient client,
      PaloAltoCortexExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      TenantScopedTransaction tenantTx) {
    this.executor = executor;
    this.client = client;
    this.config = config;
    this.deviceSync = new DeviceSync(endpointService, agentService, assetGroupService, tenantTx);
  }

  @Override
  public void run() {
    deviceSync.sync(executor, this::devices);
  }

  private List<RemoteDevice> devices() {
    log.info("Running Palo Alto Cortex executor endpoints gathering...");
    List<String> groupNames = Stream.of(this.config.getGroupName().split(",")).distinct().toList();
    return DeviceGroup.readEach(groupNames, this::groupDevices);
  }

  private List<RemoteDevice> groupDevices(String groupName) {
    List<PaloAltoCortexEndpoint> endpoints = this.client.endpoints(groupName);
    log.info(
        "Palo Alto Cortex executor provisioning based on {} assets for the group {}",
        endpoints.size(),
        groupName);
    return RemoteDevice.inGroup(
        toAgentEndpoint(endpoints),
        new DeviceGroup(PALOALTOCORTEX_EXECUTOR_TYPE + "_" + groupName, groupName, null));
  }

  private List<AgentRegisterInput> toAgentEndpoint(List<PaloAltoCortexEndpoint> endpoints) {
    return endpoints.stream()
        .map(
            paloAltoCortexEndpoint -> {
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(executor);
              input.setExternalReference(paloAltoCortexEndpoint.getEndpoint_id());
              input.setElevated(true);
              input.setService(true);
              input.setName(paloAltoCortexEndpoint.getEndpoint_name());
              input.setSeenIp(paloAltoCortexEndpoint.getPublic_ip());
              input.setIps(paloAltoCortexEndpoint.getIp());
              input.setMacAddresses(paloAltoCortexEndpoint.getMac_address());
              input.setHostname(paloAltoCortexEndpoint.getEndpoint_name());
              input.setPlatform(toPlatform(paloAltoCortexEndpoint.getOs_type()));
              input.setArch(Endpoint.PLATFORM_ARCH.x86_64); // No arch from API
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(Instant.ofEpochMilli(paloAltoCortexEndpoint.getLast_seen()));
              return input;
            })
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
