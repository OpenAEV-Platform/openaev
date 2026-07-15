package io.openaev.service.chaining;

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

  /**
   * Returns the asset groups (ASSET_GROUP_ID allowlist rules) that are not explicitly denied as a
   * whole group. Used at inject execution time to populate inject.assetGroups.
   */
  public List<AssetGroup> getValidAssetGroupsFromScope(String workflowId) {
    List<WorkflowScopeRule> allRules = workflowScopeRuleRepository.findAllByWorkflowId(workflowId);

    Set<String> deniedGroupIds =
        allRules.stream()
            .filter(rule -> ScopeRuleSelectedMode.DENYLIST.equals(rule.getSelectedMode()))
            .filter(rule -> ScopeRuleValueType.ASSET_GROUP_ID.equals(rule.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .collect(Collectors.toSet());

    List<String> allowedGroupIds =
        allRules.stream()
            .filter(rule -> ScopeRuleSelectedMode.ALLOWLIST.equals(rule.getSelectedMode()))
            .filter(rule -> ScopeRuleValueType.ASSET_GROUP_ID.equals(rule.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .filter(id -> !deniedGroupIds.contains(id))
            .toList();

    return allowedGroupIds.isEmpty() ? List.of() : assetGroupService.assetGroups(allowedGroupIds);
  }

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
   * Returns the raw IP/subnet strings from IP and IP_SUBNET allowlist rules that are not denied.
   *
   * <p>Each returned value is suitable for use as a manual target (e.g. in the {@code targets}
   * field of an inject content with {@code target_selector = "manual"}). Subnets are returned in
   * their CIDR notation as-is, since tools like nmap accept them directly.
   *
   * <p>Denylist filtering: an individual IP is removed if it exactly matches a denied IP rule or
   * falls inside a denied IP_SUBNET rule. A subnet value is removed only on exact match against a
   * denied IP_SUBNET rule (partial overlap is not computed).
   */
  public List<String> getValidIpsFromScope(String workflowId) {
    List<WorkflowScopeRule> allRules = workflowScopeRuleRepository.findAllByWorkflowId(workflowId);

    Set<String> deniedIps =
        allRules.stream()
            .filter(r -> ScopeRuleSelectedMode.DENYLIST.equals(r.getSelectedMode()))
            .filter(r -> ScopeRuleValueType.IP.equals(r.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .collect(Collectors.toSet());

    Set<String> deniedSubnets =
        allRules.stream()
            .filter(r -> ScopeRuleSelectedMode.DENYLIST.equals(r.getSelectedMode()))
            .filter(r -> ScopeRuleValueType.IP_SUBNET.equals(r.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .collect(Collectors.toSet());

    return allRules.stream()
        .filter(r -> ScopeRuleSelectedMode.ALLOWLIST.equals(r.getSelectedMode()))
        .filter(
            r ->
                ScopeRuleValueType.IP.equals(r.getValueType())
                    || ScopeRuleValueType.IP_SUBNET.equals(r.getValueType()))
        .map(WorkflowScopeRule::getRuleValue)
        .filter(
            value -> {
              if (deniedIps.contains(value)) return false;
              if (deniedSubnets.contains(value)) return false;
              return deniedSubnets.stream()
                  .noneMatch(subnet -> IpAddressUtils.isIpInSubnet(value, subnet));
            })
        .toList();
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
