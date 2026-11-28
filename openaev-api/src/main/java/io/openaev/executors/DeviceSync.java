package io.openaev.executors;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Executor;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.model.DeviceGroup;
import io.openaev.executors.model.RemoteDevice;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Writes the devices an EDR reports as the agents, endpoints and asset groups of its executor. */
public class DeviceSync {

  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;

  public DeviceSync(
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      TenantScopedTransaction tenantTx) {
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.tenantTx = tenantTx;
  }

  /** Reads the devices outside any transaction, then writes them in the executor's tenant. */
  public void sync(Executor executor, Supplier<List<RemoteDevice>> devices) {
    List<RemoteDevice> remoteDevices = devices.get();
    if (remoteDevices.isEmpty()) {
      return;
    }
    tenantTx.execute(
        TxCtx.forTenant(executor.getTenantId()),
        () -> {
          write(executor, remoteDevices);
          return null;
        });
  }

  private void write(Executor executor, List<RemoteDevice> remoteDevices) {
    String tenantId = executor.getTenantId();
    Map<String, AgentRegisterInput> agentsByReference = new LinkedHashMap<>();
    Map<String, DeviceGroup> groupsByReference = new LinkedHashMap<>();
    Map<String, Set<String>> agentReferencesByGroup = new LinkedHashMap<>();
    for (RemoteDevice device : remoteDevices) {
      String agentReference = device.agent().getExternalReference();
      // A device in several groups is reported once per group: sync it once
      agentsByReference.putIfAbsent(agentReference, device.agent());
      for (DeviceGroup group : device.groups()) {
        groupsByReference.putIfAbsent(group.externalReference(), group);
        agentReferencesByGroup
            .computeIfAbsent(group.externalReference(), k -> new LinkedHashSet<>())
            .add(agentReference);
      }
    }
    List<Agent> agents =
        endpointService.syncAgentsEndpoints(
            new ArrayList<>(agentsByReference.values()),
            agentService.getAgentsByExecutorIdAndTenantId(executor.getId(), tenantId),
            tenantId);
    Map<String, Asset> assetsByAgentReference =
        agents.stream()
            .collect(Collectors.toMap(Agent::getExternalReference, Agent::getAsset, (a, b) -> a));
    groupsByReference.forEach(
        (groupReference, group) ->
            assetGroupService.syncExternalAssetGroup(
                tenantId,
                groupReference,
                group.name(),
                group.description(),
                assetsOf(agentReferencesByGroup.get(groupReference), assetsByAgentReference)));
  }

  private static List<Asset> assetsOf(
      Set<String> agentReferences, Map<String, Asset> assetsByAgentReference) {
    return agentReferences.stream()
        .map(assetsByAgentReference::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
