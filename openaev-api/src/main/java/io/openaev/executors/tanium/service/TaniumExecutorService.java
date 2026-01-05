package io.openaev.executors.tanium.service;

import static io.openaev.utils.TimeUtils.toInstant;

import io.openaev.database.model.*;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.TaniumComputerGroup;
import io.openaev.executors.tanium.model.TaniumEndpoint;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaniumExecutorService implements Runnable {
  private final TaniumExecutorClient client;
  private final TaniumExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform) {
      case "Linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "Windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "MacOS", "Mac" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotBlank final String arch) {
    return switch (arch) {
      case "x64-based PC", "x86_64" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "arm64-based PC", "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> Endpoint.PLATFORM_ARCH.Unknown;
    };
  }

  public TaniumExecutorService(
      Executor executor,
      TaniumExecutorClient client,
      TaniumExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.executor = executor;
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
  }

  @Override
  public void run() {
    log.info("Running Tanium executor endpoints gathering...");
    List<String> computerGroupIds =
        Stream.of(this.config.getComputerGroupId().split(",")).distinct().toList();
    for (String computerGroupId : computerGroupIds) {
      TaniumComputerGroup computerGroup =
          this.client.computerGroup(computerGroupId).getComputerGroup();
      List<NodeEndpoint> nodeEndpoints = this.client.endpoints(computerGroupId);
      if (!nodeEndpoints.isEmpty()) {
        Optional<AssetGroup> existingAssetGroup =
            assetGroupService.findByExternalReference(computerGroupId);
        AssetGroup assetGroup;
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(computerGroupId);
        }
        assetGroup.setName(computerGroup.getName());
        log.info(
            "Tanium executor provisioning based on "
                + nodeEndpoints.size()
                + " assets for the computer group "
                + assetGroup.getName());
        List<Agent> agents =
            endpointService.syncAgentsEndpoints(
                toAgentEndpoint(nodeEndpoints),
                agentService.getAgentsByExecutorType(
                    TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE));
        assetGroup.setAssets(agents.stream().map(Agent::getAsset).toList());
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
      }
    }
  }

  // -- PRIVATE --

  private List<AgentRegisterInput> toAgentEndpoint(
      @NotNull final List<NodeEndpoint> nodeEndpoints) {
    return nodeEndpoints.stream()
        .map(
            nodeEndpoint -> {
              TaniumEndpoint taniumEndpoint = nodeEndpoint.getNode();
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(this.executor);
              input.setExternalReference(taniumEndpoint.getId());
              input.setElevated(true);
              input.setService(true);
              input.setName(taniumEndpoint.getName());
              input.setIps(taniumEndpoint.getIpAddresses());
              input.setMacAddresses(taniumEndpoint.getMacAddresses());
              input.setHostname(taniumEndpoint.getName());
              input.setPlatform(toPlatform(taniumEndpoint.getOs().getPlatform()));
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setArch(toArch(taniumEndpoint.getProcessor().getArchitecture()));
              input.setLastSeen(toInstant(taniumEndpoint.getEidLastSeen()));
              return input;
            })
        .collect(Collectors.toList());
  }
}
