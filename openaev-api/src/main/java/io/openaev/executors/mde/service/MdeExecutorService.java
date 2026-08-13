package io.openaev.executors.mde.service;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Tenant;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MdeExecutorService implements Runnable {

  // Advanced Hunting look-back window used to build the near real-time device activity map. Kept
  // comfortably above OpenAEV's 1h active threshold so the accurate activity timestamp (not this
  // window) decides whether an agent is active.
  private static final int RECENT_ACTIVITY_WINDOW_MINUTES = 180;

  private final MdeExecutorClient client;
  private final MdeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;
  private Executor executor;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotNull final String osPlatform) {
    if (osPlatform == null) return Endpoint.PLATFORM_TYPE.Unknown;
    return switch (osPlatform) {
      case "Windows10",
              "Windows11",
              "WindowsServer2016",
              "WindowsServer2019",
              "WindowsServer2022",
              "Windows" ->
          Endpoint.PLATFORM_TYPE.Windows;
      case "Linux", "Ubuntu", "Debian", "RHEL", "CentOS", "Fedora", "SLES" ->
          Endpoint.PLATFORM_TYPE.Linux;
      case "macOS", "MacOS", "Mac" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotNull final String arch) {
    if (arch == null) return Endpoint.PLATFORM_ARCH.x86_64;
    return switch (arch) {
      case "64-bit", "x64", "x86_64" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "Arm64", "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> Endpoint.PLATFORM_ARCH.x86_64;
    };
  }

  public MdeExecutorService(
      Executor executor,
      MdeExecutorClient client,
      MdeExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      TenantScopedTransaction tenantTx) {
    this.executor = executor;
    this.client = client;
    this.config = config;
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
    try {
      log.info(
          "Running MDE executor endpoints gathering... executorId={}, deviceGroup={}",
          executor.getId(),
          config.getDeviceGroup());
      String rawGroup = config.getDeviceGroup();
      boolean noGroupConfigured = rawGroup == null || rawGroup.isBlank();

      // Query Advanced Hunting once per sync for near real-time device activity (the machines
      // inventory lastSeen lags by up to a day). null means it is unavailable (missing
      // AdvancedQuery.Read.All) and callers fall back to the sensor health flag.
      Map<String, Instant> recentActivity =
          client.getRecentDeviceActivity(RECENT_ACTIVITY_WINDOW_MINUTES);
      boolean advancedHuntingAvailable = recentActivity != null;

      if (noGroupConfigured) {
        // No device group configured — fetch all machines and register without asset group
        List<MdeDevice> devices = client.devicesAll();
        if (devices.isEmpty()) {
          log.info("No active MDE devices found");
          return;
        }
        log.info("MDE executor provisioning {} devices (no group filter)", devices.size());
        endpointService.syncAgentsEndpoints(
            toAgentEndpoint(devices, recentActivity, advancedHuntingAvailable),
            agentService.getAgentsByExecutorIdAndTenantId(executor.getId(), executor.getTenantId()),
            executor.getTenantId());
        return;
      }

      List<String> deviceGroupIds =
          Stream.of(rawGroup.split(","))
              .map(String::trim)
              .filter(s -> !s.isBlank())
              .distinct()
              .toList();

      for (String groupId : deviceGroupIds) {
        List<MdeDevice> devices = client.devices(groupId);
        if (devices.isEmpty()) {
          log.info("No active MDE devices found for device group id={}", groupId);
          continue;
        }
        // Name the AssetGroup after the real device group. MDE exposes no "list machine groups"
        // endpoint, so the name is taken from the devices themselves — the /machines API returns
        // rbacGroupName alongside rbacGroupId. Falls back to a generic label if it is absent.
        String groupName =
            devices.stream()
                .map(MdeDevice::getRbacGroupName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("MDE Device Group " + groupId);
        Optional<AssetGroup> existingAssetGroup =
            assetGroupService.findByExternalReference(groupId, executor.getTenantId());
        AssetGroup assetGroup = existingAssetGroup.orElseGet(AssetGroup::new);
        assetGroup.setExternalReference(groupId);
        assetGroup.setTenant(new Tenant(executor.getTenantId()));
        assetGroup.setName(groupName);
        log.info(
            "MDE executor provisioning {} devices for group '{}'",
            devices.size(),
            assetGroup.getName());
        List<Agent> agents =
            endpointService.syncAgentsEndpoints(
                toAgentEndpoint(devices, recentActivity, advancedHuntingAvailable),
                agentService.getAgentsByExecutorIdAndTenantId(
                    executor.getId(), executor.getTenantId()),
                executor.getTenantId());
        assetGroup.setAssets(
            agents.stream().map(Agent::getAsset).collect(Collectors.toCollection(ArrayList::new)));
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
      }
    } catch (Exception e) {
      log.error("Error during MDE executor endpoint gathering: {}", e.getMessage(), e);
    }
  }

  private List<AgentRegisterInput> toAgentEndpoint(
      @NotNull final List<MdeDevice> devices,
      final Map<String, Instant> recentActivity,
      final boolean advancedHuntingAvailable) {
    return devices.stream()
        .map(
            device -> {
              List<String> ips = new ArrayList<>();
              if (device.getLastIpAddress() != null) ips.add(device.getLastIpAddress());
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(executor);
              input.setExternalReference(device.getId());
              input.setElevated(true);
              input.setService(true);
              input.setName(device.getComputerDnsName());
              input.setHostname(device.getComputerDnsName());
              input.setSeenIp(device.getLastExternalIpAddress());
              input.setIps(ips.toArray(new String[0]));
              input.setMacAddresses(new String[0]);
              Endpoint.PLATFORM_TYPE platform = toPlatform(device.getOsPlatform());
              input.setPlatform(platform);
              input.setArch(
                  device.getOsArchitecture() != null
                      ? toArch(device.getOsArchitecture())
                      : Endpoint.PLATFORM_ARCH.x86_64);
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(platform)
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(resolveLastSeen(device, recentActivity, advancedHuntingAvailable));
              return input;
            })
        .collect(Collectors.toList());
  }

  /**
   * Resolves the agent lastSeen used for the active status. Prefers the near real-time Advanced
   * Hunting activity; when Advanced Hunting is unavailable, approximates reachability with the MDE
   * sensor health flag rather than the badly lagging inventory lastSeen.
   */
  private static Instant resolveLastSeen(
      MdeDevice device, Map<String, Instant> recentActivity, boolean advancedHuntingAvailable) {
    if (advancedHuntingAvailable) {
      Instant fresh = recentActivity.get(device.getId());
      // A device absent from the activity window has not been active recently, so its stale
      // inventory lastSeen surfaces it as inactive.
      return fresh != null ? fresh : parseDeviceLastSeen(device.getLastSeen());
    }
    return "Active".equalsIgnoreCase(device.getHealthStatus())
        ? Instant.now()
        : parseDeviceLastSeen(device.getLastSeen());
  }

  /**
   * Parses the MDE device {@code lastSeen} (ISO-8601 UTC) into an {@link Instant}. Falls back to
   * {@link Instant#EPOCH} when the value is missing or unparseable so the agent is treated as
   * inactive rather than falsely reported active.
   */
  private static Instant parseDeviceLastSeen(String lastSeen) {
    if (lastSeen == null || lastSeen.isBlank()) {
      return Instant.EPOCH;
    }
    try {
      return Instant.parse(lastSeen);
    } catch (DateTimeParseException e) {
      log.warn("Could not parse MDE device lastSeen '{}': {}", lastSeen, e.getMessage());
      return Instant.EPOCH;
    }
  }

  @VisibleForTesting
  protected void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
