package io.openaev.executors.crowdstrike.service;

import static io.openaev.utils.TimeUtils.toInstant;

import io.openaev.database.model.*;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeDevice;
import io.openaev.executors.crowdstrike.model.CrowdStrikeHostGroup;
import io.openaev.executors.crowdstrike.model.CrowdstrikeError;
import io.openaev.executors.crowdstrike.model.ResourcesGroups;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration;
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
public class CrowdStrikeExecutorService implements Runnable {
  private final CrowdStrikeExecutorClient client;
  private final CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform) {
      case "Linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "Windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "Mac" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotBlank final String arch) {
    return switch (arch) {
      case "x64", "x86_64" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> Endpoint.PLATFORM_ARCH.Unknown;
    };
  }

  public CrowdStrikeExecutorService(
      Executor executor,
      CrowdStrikeExecutorClient client,
      CrowdStrikeExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executor = executor;
  }

  @Override
  public void run() {
    log.info("Running CrowdStrike executor endpoints gathering...");
    List<String> hostGroups = Stream.of(this.config.getHostGroup().split(",")).distinct().toList();
    ResourcesGroups crowdStrikeResourceGroup;
    CrowdStrikeHostGroup crowdStrikeHostGroup;
    for (String hostGroup : hostGroups) {
      crowdStrikeResourceGroup = this.client.hostGroup(hostGroup);
      if (crowdStrikeResourceGroup.getErrors() != null
          && !crowdStrikeResourceGroup.getErrors().isEmpty()) {
        logErrors(crowdStrikeResourceGroup.getErrors(), hostGroup);
        continue;
      }
      List<CrowdStrikeDevice> devices = this.client.devices(hostGroup);
      if (!devices.isEmpty()) {
        Optional<AssetGroup> existingAssetGroup =
            assetGroupService.findByExternalReference(hostGroup);
        AssetGroup assetGroup;
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(hostGroup);
        }
        crowdStrikeHostGroup = crowdStrikeResourceGroup.getResources().getFirst();
        assetGroup.setName(crowdStrikeHostGroup.getName());
        assetGroup.setDescription(crowdStrikeHostGroup.getDescription());
        log.info(
            "CrowdStrike executor provisioning based on "
                + devices.size()
                + " assets for the host group "
                + assetGroup.getName());
        List<Agent> agents =
            endpointService.syncAgentsEndpoints(
                toAgentEndpoint(devices),
                agentService.getAgentsByExecutorType(
                    CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE));
        assetGroup.setAssets(agents.stream().map(Agent::getAsset).toList());
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
      }
    }
  }

  // -- PRIVATE --

  private void logErrors(List<CrowdstrikeError> errors, String hostGroup) {
    StringBuilder msg =
        new StringBuilder(
            "Error occurred while getting Crowdstrike hostGroup API request for id "
                + hostGroup
                + ".");
    for (CrowdstrikeError error : errors) {
      msg.append("\nCode: ")
          .append(error.getCode())
          .append(", message: ")
          .append(error.getMessage())
          .append(".");
    }
    log.error(msg.toString());
  }

  private List<AgentRegisterInput> toAgentEndpoint(@NotNull final List<CrowdStrikeDevice> devices) {
    return devices.stream()
        .map(
            crowdStrikeDevice -> {
              List<String> ips = new ArrayList<>();
              if (crowdStrikeDevice.getConnection_ip() != null) {
                ips.add(crowdStrikeDevice.getConnection_ip());
              }
              if (crowdStrikeDevice.getLocal_ip() != null) {
                ips.add(crowdStrikeDevice.getLocal_ip());
              }
              List<String> macAddresses = new ArrayList<>();
              if (crowdStrikeDevice.getMac_address() != null) {
                macAddresses.add(crowdStrikeDevice.getMac_address());
              }
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(this.executor);
              input.setExternalReference(crowdStrikeDevice.getDevice_id());
              input.setElevated(true);
              input.setService(true);
              input.setName(crowdStrikeDevice.getHostname());
              input.setSeenIp(crowdStrikeDevice.getExternal_ip());
              input.setIps(ips.toArray(new String[0]));
              input.setMacAddresses(macAddresses.toArray(new String[0]));
              input.setHostname(crowdStrikeDevice.getHostname());
              input.setPlatform(toPlatform(crowdStrikeDevice.getPlatform_name()));
              input.setArch(toArch("x64"));
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(toInstant(crowdStrikeDevice.getLast_seen()));
              return input;
            })
        .collect(Collectors.toList());
  }
}
