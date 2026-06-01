package io.openaev.executors.tanium.service;

import static io.openaev.utils.time.TimeUtils.toInstant;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.TaniumComputerGroup;
import io.openaev.executors.tanium.model.TaniumEndpoint;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class TaniumExecutorService implements Runnable {
  private final TaniumExecutorClient client;
  private final TaniumExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final TransactionTemplate transactionTemplate;
  private final EntityManagerFactory entityManagerFactory;
  private Executor executor;

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
      AssetGroupService assetGroupService,
      TransactionTemplate transactionTemplate,
      EntityManagerFactory entityManagerFactory) {
    this.executor = executor;
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.transactionTemplate = transactionTemplate;
    this.entityManagerFactory = entityManagerFactory;
  }

  @Override
  public void run() {
    String tenantId = executor.getTenantId();
    TenantContext.setCurrentTenant(tenantId);
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            // Enable tenant filter on the Session created by TransactionTemplate
            EntityManager em =
                EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            if (em != null) {
              em.unwrap(Session.class)
                  .enableFilter("tenantFilter")
                  .setParameter("tenantId", tenantId);
            }
            doRun(tenantId);
          });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void doRun(String tenantId) {
    log.info("Running Tanium executor endpoints gathering...");
    List<String> computerGroupIds =
        Stream.of(this.config.getComputerGroupId().split(",")).distinct().toList();
    for (String computerGroupId : computerGroupIds) {
      TaniumComputerGroup computerGroup =
          this.client.computerGroup(computerGroupId).getComputerGroup();
      List<NodeEndpoint> nodeEndpoints = this.client.endpoints(computerGroupId);
      if (!nodeEndpoints.isEmpty()) {
        Optional<AssetGroup> existingAssetGroup =
            assetGroupService.findByExternalReference(computerGroupId, tenantId);
        AssetGroup assetGroup;
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(computerGroupId);
          assetGroup.setTenant(executor.getTenant());
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
                agentService.getAgentsByExecutorId(executor.getId(), tenantId));
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

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
