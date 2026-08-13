package io.openaev.service.chaining;

import static io.openaev.utils.JsonUtils.gson;

import io.openaev.database.model.*;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.EndpointService;
import io.openaev.utils.IpAddressUtils;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
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
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final EndpointService endpointService;
  private final EndpointMapper endpointMapper;
  private final AssetGroupMapper assetGroupMapper;

  /**
   * Returns the endpoints explicitly listed in the workflow scope rules ({@code ASSET_ID} rules),
   * whatever the rule mode.
   *
   * <p>Denylisted assets are included on purpose: the scope screen has to label both the allow and
   * the deny column. This is the workflow-scoped counterpart of {@code GET /api/endpoints}, so a
   * user granted on the parent simulation/scenario can read them without the global asset
   * capabilities.
   *
   * @param workflowId the workflow whose scope rules are read
   * @return the endpoints referenced by the scope rules
   */
  public List<EndpointOutput> getScopeEndpoints(final String workflowId) {
    Set<String> assetIds = collectScopeRuleValues(workflowId, ScopeRuleValueType.ASSET_ID);
    return resolveEndpoints(assetIds);
  }

  /**
   * Same as {@link #getScopeEndpoints(String)} but restricted to the requested IDs. IDs that are
   * not part of the workflow scope rules are silently ignored so the endpoint cannot be used to
   * read arbitrary assets.
   *
   * @param workflowId the workflow whose scope rules are read
   * @param endpointIds the endpoint IDs to resolve
   * @return the matching endpoints
   */
  public List<EndpointOutput> getScopeEndpointsByIds(
      final String workflowId, final List<String> endpointIds) {
    Set<String> allowedIds = collectScopeRuleValues(workflowId, ScopeRuleValueType.ASSET_ID);
    allowedIds.retainAll(new HashSet<>(endpointIds));
    return resolveEndpoints(allowedIds);
  }

  /**
   * Returns the asset groups explicitly listed in the workflow scope rules ({@code ASSET_GROUP_ID}
   * rules), whatever the rule mode. See {@link #getScopeEndpoints(String)} for the rationale.
   *
   * @param workflowId the workflow whose scope rules are read
   * @return the asset groups referenced by the scope rules
   */
  public List<AssetGroupOutput> getScopeAssetGroups(final String workflowId) {
    Set<String> assetGroupIds =
        collectScopeRuleValues(workflowId, ScopeRuleValueType.ASSET_GROUP_ID);
    return resolveAssetGroups(assetGroupIds);
  }

  /**
   * Same as {@link #getScopeAssetGroups(String)} but restricted to the requested IDs. IDs that are
   * not part of the workflow scope rules are silently ignored.
   *
   * @param workflowId the workflow whose scope rules are read
   * @param assetGroupIds the asset group IDs to resolve
   * @return the matching asset groups
   */
  public List<AssetGroupOutput> getScopeAssetGroupsByIds(
      final String workflowId, final List<String> assetGroupIds) {
    Set<String> allowedIds = collectScopeRuleValues(workflowId, ScopeRuleValueType.ASSET_GROUP_ID);
    allowedIds.retainAll(new HashSet<>(assetGroupIds));
    return resolveAssetGroups(allowedIds);
  }

  private List<EndpointOutput> resolveEndpoints(final Set<String> endpointIds) {
    return endpointIds.isEmpty()
        ? List.of()
        : endpointService.endpoints(List.copyOf(endpointIds)).stream()
            .map(endpointMapper::toEndpointOutput)
            .toList();
  }

  private List<AssetGroupOutput> resolveAssetGroups(final Set<String> assetGroupIds) {
    return assetGroupIds.isEmpty()
        ? List.of()
        : assetGroupService.assetGroups(List.copyOf(assetGroupIds)).stream()
            .map(assetGroupMapper::toAssetGroupOutput)
            .toList();
  }

  private Set<String> collectScopeRuleValues(
      final String workflowId, final ScopeRuleValueType valueType) {
    return workflowScopeRuleRepository.findAllByWorkflowId(workflowId).stream()
        .filter(rule -> valueType.equals(rule.getValueType()))
        .map(WorkflowScopeRule::getRuleValue)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
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

  /**
   * Returns the teams that are in scope for the given workflow: allowlisted TEAM rules minus
   * denylisted TEAM rules. Mirrors {@link #getValidAssets(String)} for the audience axis.
   */
  public List<Team> getValidTeams(String workflowId) {
    List<String> keptTeamIds =
        resolveAllowedMinusDeniedIds(
            workflowScopeRuleRepository.findAllByWorkflowId(workflowId),
            ScopeRuleValueType.TEAM_ID);
    return keptTeamIds.isEmpty() ? List.of() : teamRepository.findAllById(keptTeamIds);
  }

  /**
   * Returns the individual players that are in scope for the given workflow: allowlisted PLAYER
   * rules minus denylisted PLAYER rules. Mirrors {@link #getValidTeams(String)} so audience-centric
   * (tabletop) injects can consume players placed directly in the scope.
   */
  public List<User> getValidPlayers(String workflowId) {
    List<String> keptPlayerIds =
        resolveAllowedMinusDeniedIds(
            workflowScopeRuleRepository.findAllByWorkflowId(workflowId),
            ScopeRuleValueType.PLAYER_ID);
    return keptPlayerIds.isEmpty() ? List.of() : userRepository.findAllById(keptPlayerIds);
  }

  /**
   * Returns the player IDs denylisted in the workflow scope. A denylisted player must never receive
   * an audience-centric inject, even as a member of an allowlisted team.
   */
  public Set<String> getDeniedPlayerIds(String workflowId) {
    return collectRuleValues(
        workflowScopeRuleRepository.findAllByWorkflowId(workflowId),
        ScopeRuleSelectedMode.DENYLIST,
        ScopeRuleValueType.PLAYER_ID);
  }

  /**
   * Applies the allowlist-minus-denylist rule for ID-valued scope rules of the given type,
   * preserving allowlist insertion order.
   */
  private List<String> resolveAllowedMinusDeniedIds(
      List<WorkflowScopeRule> allRules, ScopeRuleValueType valueType) {
    Set<String> allowedIds =
        allRules.stream()
            .filter(rule -> ScopeRuleSelectedMode.ALLOWLIST.equals(rule.getSelectedMode()))
            .filter(rule -> valueType.equals(rule.getValueType()))
            .map(WorkflowScopeRule::getRuleValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (allowedIds.isEmpty()) {
      return List.of();
    }

    Set<String> deniedIds = collectRuleValues(allRules, ScopeRuleSelectedMode.DENYLIST, valueType);
    return allowedIds.stream().filter(id -> !deniedIds.contains(id)).toList();
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
   * addresses are excluded when applicable (e.g. /26 -> .1.. .62). To keep execution bounded, IPv4
   * expansion is supported only for /24-or-narrower subnets. IP and domain rules are kept as direct
   * targets.
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
