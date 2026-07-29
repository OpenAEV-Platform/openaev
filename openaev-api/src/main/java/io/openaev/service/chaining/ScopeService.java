package io.openaev.service.chaining;

import static io.openaev.utils.JsonUtils.gson;

import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.utils.IpAddressUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
   * Returns the asset groups (ASSET_GROUP_ID allowlist rules) that are not explicitly denied as a
   * whole group. Used at inject execution time to populate inject.assetGroups.
   */
  public List<AssetGroup> getValidAssetGroupsFromScope(String workflowId) {
    List<WorkflowScopeRule> allRules = workflowScopeRuleRepository.findAllByWorkflowId(workflowId);

    PrimitiveValidationContext context =
        new PrimitiveValidationContext(
            collectRuleValues(
                allRules, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_GROUP_ID),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            collectRuleValues(
                allRules, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.ASSET_GROUP_ID),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of());

    List<String> allowedGroupIds =
        allRules.stream()
            .filter(r -> ScopeRuleSelectedMode.ALLOWLIST.equals(r.getSelectedMode()))
            .filter(r -> ScopeRuleValueType.ASSET_GROUP_ID.equals(r.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .filter(id -> PrimitiveValueValidator.isAssetGroupIdAllowedByScope(id, context))
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
   * Returns the raw IP, subnet, and domain strings from allowlist rules that are not denied.
   *
   * <p>Each returned value is suitable for use as a manual target (e.g. in the {@code targets}
   * field of an inject content with {@code target_selector = "manual"}). Subnets are returned in
   * their CIDR notation as-is, since tools like nmap accept them directly. Domains (hostnames) from
   * DOMAIN rules are included alongside IPs and subnets.
   *
   * <p>Denylist filtering: an individual IP is removed if it exactly matches a denied IP rule or
   * falls inside a denied IP_SUBNET rule. A subnet value is removed only on exact match against a
   * denied IP_SUBNET rule (partial overlap is not computed). A domain is removed only on exact
   * case-insensitive match against a denied DOMAIN rule.
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

    Stream<String> scopeTargets =
        allRules.stream()
            .filter(r -> ScopeRuleSelectedMode.ALLOWLIST.equals(r.getSelectedMode()))
            .filter(
                r ->
                    ScopeRuleValueType.IP.equals(r.getValueType())
                        || ScopeRuleValueType.IP_SUBNET.equals(r.getValueType())
                        || ScopeRuleValueType.DOMAIN.equals(r.getValueType()))
            .filter(
                r ->
                    switch (r.getValueType()) {
                      case IP ->
                          PrimitiveValueValidator.isIpAllowedByScope(r.getRuleValue(), context);
                      case IP_SUBNET ->
                          PrimitiveValueValidator.isSubnetAllowedByScope(r.getRuleValue(), context);
                      case DOMAIN ->
                          PrimitiveValueValidator.isDomainAllowedByScope(r.getRuleValue(), context);
                      default -> false;
                    })
            .map(WorkflowScopeRule::getRuleValue);

    Stream<String> globalStateIps =
        getIpsFromGlobalState(workflowId).stream()
            .filter(ip -> !PrimitiveValueValidator.isIpDeniedByScope(ip, context));

    // Preserve order (scope targets first, then global-state IPs) and deduplicate.
    return Stream.concat(scopeTargets, globalStateIps)
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
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
