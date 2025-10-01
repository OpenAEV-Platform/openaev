package io.openaev.executors.tanium.service;

import static io.openaev.utils.TimeUtils.toInstant;

import io.openaev.database.model.*;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.TaniumEndpoint;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.tanium", name = "enable")
@Slf4j
@Service
public class TaniumExecutorService implements Runnable {

  public static final String TANIUM_EXECUTOR_TYPE = "openaev_tanium";
  public static final String TANIUM_EXECUTOR_NAME = "Tanium";
  private static final String TANIUM_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#tanium-agent";
  private static final String TANIUM_EXECUTOR_BACKGROUND_COLOR = "#E03E41";

  private final TaniumExecutorClient client;

  private final EndpointService endpointService;

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

  @Autowired
  public TaniumExecutorService(
      ExecutorService executorService,
      TaniumExecutorClient client,
      TaniumExecutorConfig config,
      EndpointService endpointService) {
    this.client = client;
    this.endpointService = endpointService;
    try {
      if (config.isEnable()) {
        this.executor =
            executorService.register(
                config.getId(),
                TANIUM_EXECUTOR_TYPE,
                TANIUM_EXECUTOR_NAME,
                TANIUM_EXECUTOR_DOCUMENTATION_LINK,
                TANIUM_EXECUTOR_BACKGROUND_COLOR,
                getClass().getResourceAsStream("/img/icon-tanium.png"),
                getClass().getResourceAsStream("/img/banner-tanium.png"),
                new String[] {
                  Endpoint.PLATFORM_TYPE.Windows.name(),
                  Endpoint.PLATFORM_TYPE.Linux.name(),
                  Endpoint.PLATFORM_TYPE.MacOS.name()
                });
      } else {
        executorService.remove(config.getId());
      }
    } catch (Exception e) {
      log.error(String.format("Error creating Tanium executor: %s", e), e);
    }
  }

  @Override
  public void run() {
    log.info("Running Tanium executor endpoints gathering...");
    List<NodeEndpoint> nodeEndpoints =
        this.client.endpoints().getEndpoints().getEdges().stream().toList();
    List<AgentRegisterInput> endpointRegisterList = toAgentEndpoint(nodeEndpoints);
    log.info("Tanium executor provisioning based on " + endpointRegisterList.size() + " assets");

    for (AgentRegisterInput input : endpointRegisterList) {
      endpointService.registerAgentEndpoint(input);
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
