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

        case DOMAIN ->
            candidates.stream()
                .filter(asset -> asset instanceof Endpoint)
                .map(asset -> (Endpoint) asset)
                .filter(endpoint -> rule.getRuleValue().equalsIgnoreCase(endpoint.getHostname()))
                .map(Asset::getId)
                .forEach(deniedAssetIds::add);
      }
    }
    return deniedAssetIds;
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
