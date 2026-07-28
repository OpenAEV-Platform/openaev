package io.openaev.service.chaining;

import static io.openaev.utils.JsonUtils.gson;

import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.utils.IpAddressUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ScopeService {

  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final WorkflowStateService workflowStateService;

  /**
   * Returns the assets that are in scope for the given workflow and that are not denied by a value
   * in the denylist
   *
   * @param workflowId
   * @return
   */
  public List<Asset> getValidAssets(String workflowId) {

    // get the workflow rules
    List<WorkflowScopeRule> allRules = workflowScopeRuleRepository.findAllByWorkflowId(workflowId);
    List<WorkflowScopeRule> allowlistRules =
        allRules.stream()
            .filter(rule -> ScopeRuleSelectedMode.ALLOWLIST.equals(rule.getSelectedMode()))
            .toList();
    List<WorkflowScopeRule> denylistRules =
        allRules.stream()
            .filter(rule -> ScopeRuleSelectedMode.DENYLIST.equals(rule.getSelectedMode()))
            .toList();

    // build the complete allowed asset set --
    Set<Asset> allowedByAssetId = getAssetsAllowedByAssetId(allowlistRules);
    Set<Asset> allowedByAssetGroup = getAssetsAllowedByAssetGroup(allowlistRules);
    Set<Asset> completeAllowedAssetList = new LinkedHashSet<>();
    completeAllowedAssetList.addAll(allowedByAssetId);
    completeAllowedAssetList.addAll(allowedByAssetGroup);

    // apply the denylist
    if (completeAllowedAssetList.isEmpty() || denylistRules.isEmpty()) {
      return List.copyOf(completeAllowedAssetList);
    }

    //  remove any asset matched by a denylist rule  and return
    Set<String> deniedAssetIds = getDeniedAssetIds(completeAllowedAssetList, denylistRules);
    return completeAllowedAssetList.stream()
        .filter(asset -> !deniedAssetIds.contains(asset.getId()))
        .toList();
  }

  private Set<Asset> getAssetsAllowedByAssetId(List<WorkflowScopeRule> allowlistRules) {
    List<String> assetIds =
        allowlistRules.stream()
            .filter(rule -> ScopeRuleValueType.ASSET_ID.equals(rule.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .toList();

    return assetIds.isEmpty() ? Set.of() : new LinkedHashSet<>(assetService.assets(assetIds));
  }

  private Set<Asset> getAssetsAllowedByAssetGroup(List<WorkflowScopeRule> allowlistRules) {
    return allowlistRules.stream()
        .filter(rule -> ScopeRuleValueType.ASSET_GROUP_ID.equals(rule.getValueType()))
        .flatMap(rule -> assetGroupService.assetsFromAssetGroup(rule.getRuleValue()).stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> getDeniedAssetIds(
      Set<Asset> candidates, List<WorkflowScopeRule> denylistRules) {
    Set<String> deniedAssetIds = new HashSet<>();
    for (WorkflowScopeRule rule : denylistRules) {
      switch (rule.getValueType()) {
        case ASSET_ID ->
            candidates.stream()
                .filter(asset -> asset.getId().equals(rule.getRuleValue()))
                .map(Asset::getId)
                .forEach(deniedAssetIds::add);

        case ASSET_GROUP_ID -> {
          Set<String> groupAssetIds =
              assetGroupService.assetsFromAssetGroup(rule.getRuleValue()).stream()
                  .map(Asset::getId)
                  .collect(Collectors.toSet());
          candidates.stream()
              .filter(asset -> groupAssetIds.contains(asset.getId()))
              .map(Asset::getId)
              .forEach(deniedAssetIds::add);
        }

        case IP ->
            candidates.stream()
                .filter(asset -> asset instanceof Endpoint)
                .map(asset -> (Endpoint) asset)
                .filter(endpoint -> endpointMatchesIp(endpoint, rule.getRuleValue()))
                .map(Asset::getId)
                .forEach(deniedAssetIds::add);

        case IP_SUBNET ->
            candidates.stream()
                .filter(asset -> asset instanceof Endpoint)
                .map(asset -> (Endpoint) asset)
                .filter(endpoint -> endpointMatchesSubnet(endpoint, rule.getRuleValue()))
                .map(Asset::getId)
                .forEach(deniedAssetIds::add);
      }
    }
    return deniedAssetIds;
  }

  /**
   * Returns manual targets from allowlist scope rules after denylist filtering.
   *
   * <p>IP_SUBNET rules are expanded to individual host IPs. For IPv4, network and broadcast
   * addresses are excluded when applicable (e.g. /26 -> .1.. .62). IP and domain rules are kept as
   * direct targets.
   *
   * <p>Denylist filtering is then applied on resulting targets: denied IPs and denied subnets
   * remove matching expanded hosts, and denied domains remove matching domain targets.
   *
   * <p>In addition to the scope rules, this method includes every IPv4 and IPv6 value discovered in
   * the workflow global pool (workflow states) of the current simulation. These global-pool IPs are
   * filtered against the scope denylist only (a denied IP or an IP inside a denied subnet is
   * excluded); the scope allowlist does not restrict them. Results are deduplicated while
   * preserving insertion order (scope targets first, then global-state IPs).
   */
  public List<String> getValidManualTargetsFromScopeAndGlobalState(String workflowId) {
    List<WorkflowScopeRule> allRules = workflowScopeRuleRepository.findAllByWorkflowId(workflowId);

    PrimitiveValidationContext context =
        new PrimitiveValidationContext(
            Set.of(),
            Set.of(),
            collectRuleValues(allRules, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.DOMAIN),
            collectRuleValues(allRules, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP),
            collectRuleValues(
                allRules, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP_SUBNET),
            Set.of(),
            Set.of(),
            collectRuleValues(allRules, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.DOMAIN),
            collectRuleValues(allRules, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP),
            collectRuleValues(
                allRules, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP_SUBNET));

    LinkedHashSet<String> validTargets = new LinkedHashSet<>();

    // 1. Process rules (allowlist mode only)
    for (WorkflowScopeRule rule : allRules) {
      if (!ScopeRuleSelectedMode.ALLOWLIST.equals(rule.getSelectedMode())) {
        continue;
      }

      switch (rule.getValueType()) {
        case IP -> {
          String ip = rule.getRuleValue();
          if (PrimitiveValueValidator.isIpAllowedByScope(ip, context)) {
            validTargets.add(ip);
          }
        }
        case DOMAIN -> {
          String domain = rule.getRuleValue();
          if (PrimitiveValueValidator.isDomainAllowedByScope(domain, context)) {
            validTargets.add(domain);
          }
        }
        case IP_SUBNET -> {
          String subnet = rule.getRuleValue();
          if (PrimitiveValueValidator.isSubnetAllowedByScope(subnet, context)) {
            for (String expandedIp : IpAddressUtils.expandSubnetToHostIps(subnet)) {
              if (PrimitiveValueValidator.isIpAllowedByScope(expandedIp, context)) {
                validTargets.add(expandedIp);
              }
            }
          }
        }
        default -> {
          // Scope rules only accept IP, IP_SUBNET, or DOMAIN.
        }
      }
    }

    // 2. Add non-denied global state IPs (appended after rule targets)
    getIpsFromGlobalState(workflowId).stream()
        .filter(ip -> !PrimitiveValueValidator.isIpDeniedByScope(ip, context))
        .forEach(validTargets::add);

    return List.copyOf(validTargets);
  }

  /**
   * Collects every IPv4 and IPv6 value stored in the workflow global state (global {@link
   * WorkflowState}) for the given workflow run.
   *
   * @param workflowId the workflow execution ID
   * @return the set of IPv4/IPv6 values found in the global state, or an empty set when the global
   *     state is missing or holds no IP entries
   */
  private Set<String> getIpsFromGlobalState(String workflowId) {
    WorkflowState globalState = workflowStateService.getGlobalStateByWorkflowId(workflowId);
    if (globalState == null || globalState.getEntries() == null) {
      return Set.of();
    }
    WorkflowStateEntries entries =
        gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);
    if (entries == null || entries.getInputs() == null) {
      return Set.of();
    }

    Set<String> ips = new LinkedHashSet<>();
    for (WorkflowStateEntries.Input input : entries.getInputs()) {
      if ((PrimitiveType.IPv4.name().equals(input.getKey())
              || PrimitiveType.IPv6.name().equals(input.getKey()))
          && input.getValues() != null) {
        ips.addAll(input.getValues());
      }
    }
    return ips;
  }

  private Set<String> collectRuleValues(
      List<WorkflowScopeRule> rules, ScopeRuleSelectedMode mode, ScopeRuleValueType valueType) {
    return rules.stream()
        .filter(r -> mode.equals(r.getSelectedMode()))
        .filter(r -> valueType.equals(r.getValueType()))
        .map(WorkflowScopeRule::getRuleValue)
        .collect(Collectors.toSet());
  }

  /** Returns {@code true} if {@code targetIp} matches any of the endpoint's IPs or its seen IP. */
  private boolean endpointMatchesIp(Endpoint endpoint, String targetIp) {
    if (targetIp == null) {
      return false;
    }
    if (endpoint.getIps() != null) {
      for (String ip : endpoint.getIps()) {
        if (targetIp.equals(ip)) {
          return true;
        }
      }
    }
    return targetIp.equals(endpoint.getSeenIp());
  }

  /**
   * Returns {@code true} if any of the endpoint's IPs or its seen IP falls within {@code subnet}.
   */
  private boolean endpointMatchesSubnet(Endpoint endpoint, String subnet) {
    if (subnet == null) {
      return false;
    }
    if (endpoint.getIps() != null) {
      for (String ip : endpoint.getIps()) {
        if (IpAddressUtils.isIpInSubnet(ip, subnet)) {
          return true;
        }
      }
    }
    return endpoint.getSeenIp() != null
        && IpAddressUtils.isIpInSubnet(endpoint.getSeenIp(), subnet);
  }
}
