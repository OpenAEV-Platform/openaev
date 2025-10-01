package io.openaev.executors.caldera.service;

import static io.openaev.service.EndpointService.DELETE_TTL;
import static io.openaev.utils.TimeUtils.toInstant;
import static java.time.Instant.now;

import com.cronutils.utils.VisibleForTesting;
import io.openaev.database.model.*;
import io.openaev.database.model.Agent.DEPLOYMENT_MODE;
import io.openaev.database.model.Agent.PRIVILEGE;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.integrations.InjectorService;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.mapper.EndpointMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CalderaExecutorService implements Runnable {

  private static final int CLEAR_TTL = 1800000; // 30 minutes
  private static final String CALDERA_EXECUTOR_TYPE = "openaev_caldera";
  public static final String CALDERA_EXECUTOR_NAME = "Caldera";
  public static final String CALDERA_BACKGROUND_COLOR = "#8B1316";

  private final CalderaExecutorClient client;

  private final EndpointService endpointService;

  private final CalderaExecutorContextService calderaExecutorContextService;

  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final AgentService agentService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform) {
      case "linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "darwin" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> throw new IllegalArgumentException("This platform is not supported : " + platform);
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotBlank final String arch) {
    return switch (arch) {
      case "amd64" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> throw new IllegalArgumentException("This arch is not supported : " + arch);
    };
  }

  @Autowired
  public CalderaExecutorService(
      ExecutorService executorService,
      CalderaExecutorClient client,
      CalderaExecutorConfig config,
      CalderaExecutorContextService calderaExecutorContextService,
      EndpointService endpointService,
      InjectorService injectorService,
      PlatformSettingsService platformSettingsService,
      AgentService agentService) {
    this.client = client;
    this.endpointService = endpointService;
    this.calderaExecutorContextService = calderaExecutorContextService;
    this.injectorService = injectorService;
    this.platformSettingsService = platformSettingsService;
    this.agentService = agentService;
    try {
      if (config.isEnable()) {
        log.warn("Caldera executor will be deprecated in OpenAEV 2.0");
        this.executor =
            executorService.register(
                config.getId(),
                CALDERA_EXECUTOR_TYPE,
                CALDERA_EXECUTOR_NAME,
                null,
                CALDERA_BACKGROUND_COLOR,
                getClass().getResourceAsStream("/img/icon-caldera.png"),
                getClass().getResourceAsStream("/img/banner-caldera.png"),
                new String[] {
                  Endpoint.PLATFORM_TYPE.Windows.name(),
                  Endpoint.PLATFORM_TYPE.Linux.name(),
                  Endpoint.PLATFORM_TYPE.MacOS.name()
                });
        this.calderaExecutorContextService.registerAbilities();
      } else {
        this.platformSettingsService.cleanMessage(BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE);
        executorService.removeFromType(CALDERA_EXECUTOR_TYPE);
      }
    } catch (Exception e) {
      log.error(String.format("Error creating caldera executor: %s", e.getMessage()), e);
    }
  }

  @Override
  public void run() {
    try {
      log.info("Running Caldera executor endpoints gathering...");
      // The executor only retrieve "main" agents (without the keyword "executor")
      // This is NOT a standard behaviour, this is because we are using Caldera as an executor and
      // we should not
      // Will be replaced by the XTM agent
      List<AgentRegisterInput> endpointRegisterList =
          toAgentEndpoint(
              this.client.agents().stream()
                  .filter(agent -> !agent.getExe_name().contains("implant"))
                  .toList());
      log.info("Caldera executor provisioning based on " + endpointRegisterList.size() + " assets");

      for (AgentRegisterInput input : endpointRegisterList) {
        registerAgentEndpoint(input);
      }
      this.platformSettingsService.cleanMessage(BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE);
    } catch (Exception e) {
      this.platformSettingsService.errorMessage(BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE);
    }
  }

  private void registerAgentEndpoint(AgentRegisterInput input) {
    // Check if agent exists (only 1 agent can be found for Caldera)
    List<Agent> existingAgents = agentService.findByExternalReference(input.getExternalReference());
    if (!existingAgents.isEmpty()) {
      Agent existingAgent = existingAgents.getFirst();
      if (input.isActive()) {
        updateExistingAgent(existingAgent, input);
      } else {
        // Delete inactive agent
        handleInactiveAgent(existingAgent);
      }
    } else {
      // Check if endpoint exists
      List<Endpoint> existingEndpoints =
          endpointService.findEndpointByHostnameAndAtLeastOneIp(
              input.getHostname(), input.getIps());
      if (existingEndpoints.size() == 1) {
        updateExistingEndpointAndManageAgent(existingEndpoints.getFirst(), input);
      } else {
        // Nothing exists, create endpoint and agent
        createNewEndpointAndAgent(input);
      }
    }
  }

  private void handleInactiveAgent(Agent existingAgent) {
    if ((now().toEpochMilli() - existingAgent.getLastSeen().toEpochMilli()) > DELETE_TTL) {
      log.info(
          "Found stale endpoint "
              + existingAgent.getAsset().getName()
              + ", deleting the "
              + CALDERA_EXECUTOR_TYPE
              + " agent "
              + existingAgent.getExecutedByUser()
              + " in it...");
      this.client.deleteAgent(existingAgent.getExternalReference());
      this.agentService.deleteAgent(existingAgent.getId());
    }
  }

  private void createOrUpdateAgent(Endpoint endpoint, AgentRegisterInput input) {
    DEPLOYMENT_MODE deploymentMode =
        input.isService() ? DEPLOYMENT_MODE.service : DEPLOYMENT_MODE.session;
    PRIVILEGE privilege = input.isElevated() ? PRIVILEGE.admin : PRIVILEGE.standard;
    Optional<Agent> existingAgent =
        agentService.getAgentForAnAsset(
            endpoint.getId(),
            input.getExecutedByUser(),
            deploymentMode,
            privilege,
            CALDERA_EXECUTOR_TYPE);
    Agent agent;
    if (existingAgent.isPresent()) {
      agent = existingAgent.get();
    } else {
      agent = new Agent();
      setNewAgentAttributes(input, agent);
    }
    setUpdatedAgentAttributes(agent, input, endpoint);
    agentService.createOrUpdateAgent(agent);
  }

  private void setNewAgentAttributes(AgentRegisterInput input, Agent agent) {
    agent.setPrivilege(input.isElevated() ? PRIVILEGE.admin : PRIVILEGE.standard);
    agent.setDeploymentMode(input.isService() ? DEPLOYMENT_MODE.service : DEPLOYMENT_MODE.session);
    agent.setExecutedByUser(input.getExecutedByUser());
    agent.setExecutor(input.getExecutor());
  }

  private void updateExistingEndpointAndManageAgent(Endpoint endpoint, AgentRegisterInput input) {
    endpoint.setHostname(input.getHostname());
    endpoint.setArch(input.getArch());
    endpoint.setIps(EndpointMapper.mergeAddressArrays(endpoint.getIps(), input.getIps()));
    endpointService.updateEndpoint(endpoint);
    createOrUpdateAgent(endpoint, input);
  }

  private void updateExistingAgent(Agent agent, AgentRegisterInput input) {
    Endpoint endpoint = (Endpoint) agent.getAsset();
    endpoint.setHostname(input.getHostname());
    endpoint.setArch(input.getArch());
    endpoint.setIps(EndpointMapper.mergeAddressArrays(endpoint.getIps(), input.getIps()));
    endpointService.updateEndpoint(endpoint);
    setUpdatedAgentAttributes(agent, input, endpoint);
    agentService.createOrUpdateAgent(agent);
  }

  private void setUpdatedAgentAttributes(Agent agent, AgentRegisterInput input, Endpoint endpoint) {
    agent.setAsset(endpoint);
    agent.setProcessName(input.getProcessName());
    agent.setLastSeen(input.getLastSeen());
    agent.setExternalReference(input.getExternalReference());
    clearAbilityForAgent(agent);
  }

  private void createNewEndpointAndAgent(AgentRegisterInput input) {
    Endpoint endpoint = new Endpoint();
    endpoint.setName(input.getName());
    endpoint.setPlatform(input.getPlatform());
    endpoint.setArch(input.getArch());
    endpoint.setHostname(input.getHostname());
    endpoint.setIps(input.getIps());
    endpointService.createEndpoint(endpoint);
    Agent agent = new Agent();
    setUpdatedAgentAttributes(agent, input, endpoint);
    setNewAgentAttributes(input, agent);
    agentService.createOrUpdateAgent(agent);
  }

  // -- PRIVATE --

  private List<AgentRegisterInput> toAgentEndpoint(
      @NotNull final List<io.openaev.executors.caldera.model.Agent> agentsCaldera) {
    return agentsCaldera.stream()
        .map(
            agent -> {
              AgentRegisterInput input = new AgentRegisterInput();
              input.setName(agent.getHost());
              input.setIps(agent.getHost_ip_addrs());
              input.setHostname(agent.getHost());
              input.setPlatform(toPlatform(agent.getPlatform()));
              input.setArch(toArch(agent.getArchitecture()));
              input.setExecutor(this.executor);
              input.setExternalReference(agent.getPaw());
              input.setElevated(true);
              input.setService(false);
              input.setExecutedByUser(agent.getUsername());
              input.setLastSeen(toInstant(agent.getLast_seen()));
              input.setProcessName(agent.getExe_name());
              return input;
            })
        .collect(Collectors.toList());
  }

  /**
   * Used to delete existing agent in Caldera application if the clear ttl is reached (that means if
   * agent Caldera is inactive in the Caldera app)
   */
  private void clearAbilityForAgent(@NotNull final Agent existingAgent) {
    if ((now().toEpochMilli() - existingAgent.getClearedAt().toEpochMilli()) > CLEAR_TTL) {
      try {
        log.info("Clearing agent caldera " + existingAgent.getExecutedByUser());
        Iterable<Injector> injectors = injectorService.injectors();
        injectors.forEach(
            injector -> {
              if (injector.getExecutorClearCommands() != null) {
                this.calderaExecutorContextService.launchExecutorClear(injector, existingAgent);
              }
            });
        existingAgent.setClearedAt(now());
      } catch (RuntimeException e) {
        log.info("Failed clear agents");
      }
    }
  }

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
