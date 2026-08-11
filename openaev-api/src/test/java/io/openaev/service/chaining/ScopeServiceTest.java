package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.EndpointService;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeService")
class ScopeServiceTest {

  private static final String WORKFLOW_ID = "workflow-1";

  @Mock private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Mock private AssetService assetService;
  @Mock private AssetGroupService assetGroupService;
  @Mock private WorkflowStateService workflowStateService;
  @Mock private EndpointService endpointService;
  @Mock private EndpointMapper endpointMapper;
  @Mock private AssetGroupMapper assetGroupMapper;

  @InjectMocks private ScopeService scopeService;

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Endpoint endpointWithSeenIp(String id, String name, String seenIp) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    endpoint.setName(name);
    endpoint.setSeenIp(seenIp);
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  private Endpoint endpointWithIps(String id, String name, String... ips) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    endpoint.setName(name);
    endpoint.setIps(ips);
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  private WorkflowScopeRule allowlistRule(ScopeRuleValueType type, String value) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
        .valueType(type)
        .ruleValue(value)
        .build();
  }

  private WorkflowScopeRule denylistRule(ScopeRuleValueType type, String value) {
    return WorkflowScopeRule.builder()
        .selectedMode(ScopeRuleSelectedMode.DENYLIST)
        .valueType(type)
        .ruleValue(value)
        .build();
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("One asset in allowlist and no denylist rules — returns the asset")
  void givenOneAssetInAllowlist_whenNoDenylistRules_thenReturnsAsset() {
    Endpoint endpoint = endpointWithSeenIp("asset-1", "host-1", "10.0.0.1");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(List.of(allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1")));
    when(assetService.assets(List.of("asset-1"))).thenReturn(List.of(endpoint));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactly(endpoint);
  }

  @Test
  @DisplayName("No allowlist rules but several denylist values — returns empty list")
  void givenNoAllowlistRules_whenSeveralDenylistValues_thenReturnsEmpty() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                denylistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                denylistRule(ScopeRuleValueType.IP, "10.0.0.1")));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Asset in allowlist, several non-matching IPs in denylist — returns the asset")
  void givenOneAssetInAllowlist_whenDenylistIpsDoNotMatch_thenReturnsAsset() {
    Endpoint endpoint = endpointWithSeenIp("asset-1", "host-1", "10.0.0.1");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                denylistRule(ScopeRuleValueType.IP, "192.168.1.1"),
                denylistRule(ScopeRuleValueType.IP, "172.16.0.1")));
    when(assetService.assets(List.of("asset-1"))).thenReturn(List.of(endpoint));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactly(endpoint);
  }

  @Test
  @DisplayName(
      "Several assets in allowlist, denylist IP matches one asset's seenIp — that asset is filtered out")
  void givenSeveralAssetsInAllowlist_whenDenylistIpMatchesSeenIp_thenMatchingAssetIsFilteredOut() {
    Endpoint targeted = endpointWithSeenIp("asset-1", "host-1", "10.0.0.1");
    Endpoint other1 = endpointWithSeenIp("asset-2", "host-2", "10.0.0.2");
    Endpoint other2 = endpointWithSeenIp("asset-3", "host-3", "10.0.0.3");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-2"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-3"),
                denylistRule(ScopeRuleValueType.IP, "10.0.0.1")));
    when(assetService.assets(List.of("asset-1", "asset-2", "asset-3")))
        .thenReturn(List.of(targeted, other1, other2));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactlyInAnyOrder(other1, other2);
    assertThat(result).doesNotContain(targeted);
  }

  @Test
  @DisplayName(
      "Several assets in allowlist, denylist IP matches one of an asset's IPs array — that asset is filtered out")
  void
      givenSeveralAssetsInAllowlist_whenDenylistIpMatchesOneOfIpsArray_thenMatchingAssetIsFilteredOut() {
    Endpoint targeted = endpointWithIps("asset-1", "host-1", "10.0.0.1", "10.0.0.99");
    Endpoint other1 = endpointWithIps("asset-2", "host-2", "10.0.0.2");
    Endpoint other2 = endpointWithIps("asset-3", "host-3", "10.0.0.3");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-2"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-3"),
                denylistRule(ScopeRuleValueType.IP, "10.0.0.99")));
    when(assetService.assets(List.of("asset-1", "asset-2", "asset-3")))
        .thenReturn(List.of(targeted, other1, other2));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactlyInAnyOrder(other1, other2);
    assertThat(result).doesNotContain(targeted);
  }

  @Test
  @DisplayName(
      "Several assets in allowlist, denylist subnet covers one asset's seenIp — that asset is filtered out")
  void
      givenSeveralAssetsInAllowlist_whenDenylistSubnetMatchesSeenIp_thenMatchingAssetIsFilteredOut() {
    Endpoint targeted = endpointWithSeenIp("asset-1", "host-1", "192.168.1.50");
    Endpoint other1 = endpointWithSeenIp("asset-2", "host-2", "10.0.0.1");
    Endpoint other2 = endpointWithSeenIp("asset-3", "host-3", "10.0.0.2");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-2"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-3"),
                denylistRule(ScopeRuleValueType.IP_SUBNET, "192.168.1.0/24")));
    when(assetService.assets(List.of("asset-1", "asset-2", "asset-3")))
        .thenReturn(List.of(targeted, other1, other2));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactlyInAnyOrder(other1, other2);
    assertThat(result).doesNotContain(targeted);
  }

  @Test
  @DisplayName(
      "Several assets in allowlist, denylist subnet covers one of an asset's IPs array — that asset is filtered out")
  void
      givenSeveralAssetsInAllowlist_whenDenylistSubnetMatchesOneOfIpsArray_thenMatchingAssetIsFilteredOut() {
    Endpoint targeted = endpointWithIps("asset-1", "host-1", "10.0.0.5", "192.168.1.10");
    Endpoint other1 = endpointWithIps("asset-2", "host-2", "10.0.0.1");
    Endpoint other2 = endpointWithIps("asset-3", "host-3", "10.0.0.2");

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-2"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-3"),
                denylistRule(ScopeRuleValueType.IP_SUBNET, "192.168.1.0/24")));
    when(assetService.assets(List.of("asset-1", "asset-2", "asset-3")))
        .thenReturn(List.of(targeted, other1, other2));

    List<Asset> result = scopeService.getValidAssets(WORKFLOW_ID);

    assertThat(result).containsExactlyInAnyOrder(other1, other2);
    assertThat(result).doesNotContain(targeted);
  }

  @Test
  @DisplayName("Allowlisted IPv4 subnet is expanded to host IP targets")
  void givenAllowlistedSubnet_whenResolvingManualTargets_thenExpandsToHostIps() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(List.of(allowlistRule(ScopeRuleValueType.IP_SUBNET, "192.168.10.0/26")));

    List<String> result = scopeService.getValidManualTargetsFromScopeAndGlobalState(WORKFLOW_ID);

    assertThat(result).hasSize(62);
    assertThat(result).contains("192.168.10.1", "192.168.10.62");
    assertThat(result).doesNotContain("192.168.10.0", "192.168.10.63");
  }

  @Test
  @DisplayName("Allowlisted subnet broader than /24 is ignored for manual target expansion")
  void givenAllowlistedSlash18Subnet_whenResolvingManualTargets_thenNoExpansionIsApplied() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(List.of(allowlistRule(ScopeRuleValueType.IP_SUBNET, "67.205.128.0/18")));

    List<String> result = scopeService.getValidManualTargetsFromScopeAndGlobalState(WORKFLOW_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Denylisted IP is removed from an expanded subnet")
  void givenDenylistedIp_whenResolvingExpandedSubnet_thenIpIsExcluded() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.IP_SUBNET, "192.168.10.0/30"),
                denylistRule(ScopeRuleValueType.IP, "192.168.10.2")));

    List<String> result = scopeService.getValidManualTargetsFromScopeAndGlobalState(WORKFLOW_ID);

    assertThat(result).containsExactly("192.168.10.1");
  }

  @Test
  @DisplayName("Denylisted subnet removes matching hosts from expanded allowlisted subnet")
  void givenDenylistedSubnet_whenResolvingExpandedSubnet_thenMatchingHostsAreExcluded() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.IP_SUBNET, "192.168.10.0/29"),
                denylistRule(ScopeRuleValueType.IP_SUBNET, "192.168.10.4/30")));

    List<String> result = scopeService.getValidManualTargetsFromScopeAndGlobalState(WORKFLOW_ID);

    assertThat(result).containsExactly("192.168.10.1", "192.168.10.2", "192.168.10.3");
  }

  // ---------------------------------------------------------------------------
  // Scope inventory (workflow-scoped endpoint / asset group listing)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Scope endpoints - ASSET_ID rules from both modes are returned, other rule types ignored")
  void givenMixedRules_whenGettingScopeEndpoints_thenReturnsAssetIdRulesFromBothModes() {
    Endpoint allowed = endpointWithSeenIp("asset-1", "host-1", "10.0.0.1");
    Endpoint denied = endpointWithSeenIp("asset-2", "host-2", "10.0.0.2");
    EndpointOutput allowedOutput = EndpointOutput.builder().id("asset-1").build();
    EndpointOutput deniedOutput = EndpointOutput.builder().id("asset-2").build();

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                denylistRule(ScopeRuleValueType.ASSET_ID, "asset-2"),
                allowlistRule(ScopeRuleValueType.ASSET_GROUP_ID, "group-1"),
                allowlistRule(ScopeRuleValueType.IP, "10.0.0.9")));
    when(endpointService.endpoints(List.of("asset-1", "asset-2")))
        .thenReturn(List.of(allowed, denied));
    when(endpointMapper.toEndpointOutput(allowed)).thenReturn(allowedOutput);
    when(endpointMapper.toEndpointOutput(denied)).thenReturn(deniedOutput);

    List<EndpointOutput> result = scopeService.getScopeEndpoints(WORKFLOW_ID);

    assertThat(result).containsExactly(allowedOutput, deniedOutput);
  }

  @Test
  @DisplayName("Scope endpoints - no ASSET_ID rules means no endpoint lookup at all")
  void givenNoAssetIdRules_whenGettingScopeEndpoints_thenReturnsEmptyWithoutLookup() {
    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(List.of(allowlistRule(ScopeRuleValueType.IP, "10.0.0.9")));

    List<EndpointOutput> result = scopeService.getScopeEndpoints(WORKFLOW_ID);

    assertThat(result).isEmpty();
    verifyNoInteractions(endpointService);
  }

  @Test
  @DisplayName("Scope endpoints by IDs - IDs outside the workflow scope rules are ignored")
  void givenIdsOutsideScopeRules_whenFindingScopeEndpoints_thenOnlyScopedIdsAreResolved() {
    Endpoint denied = endpointWithSeenIp("asset-2", "host-2", "10.0.0.2");
    EndpointOutput deniedOutput = EndpointOutput.builder().id("asset-2").build();

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1"),
                denylistRule(ScopeRuleValueType.ASSET_ID, "asset-2")));
    when(endpointService.endpoints(List.of("asset-2"))).thenReturn(List.of(denied));
    when(endpointMapper.toEndpointOutput(denied)).thenReturn(deniedOutput);

    List<EndpointOutput> result =
        scopeService.getScopeEndpointsByIds(WORKFLOW_ID, List.of("asset-2", "asset-3"));

    assertThat(result).containsExactly(deniedOutput);
  }

  @Test
  @DisplayName(
      "Scope asset groups - ASSET_GROUP_ID rules from both modes are returned, other rule types ignored")
  void givenMixedRules_whenGettingScopeAssetGroups_thenReturnsAssetGroupIdRulesFromBothModes() {
    AssetGroup allowed = new AssetGroup();
    allowed.setId("group-1");
    AssetGroup denied = new AssetGroup();
    denied.setId("group-2");
    AssetGroupOutput allowedOutput = AssetGroupOutput.builder().id("group-1").build();
    AssetGroupOutput deniedOutput = AssetGroupOutput.builder().id("group-2").build();

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(
            List.of(
                allowlistRule(ScopeRuleValueType.ASSET_GROUP_ID, "group-1"),
                denylistRule(ScopeRuleValueType.ASSET_GROUP_ID, "group-2"),
                allowlistRule(ScopeRuleValueType.ASSET_ID, "asset-1")));
    when(assetGroupService.assetGroups(List.of("group-1", "group-2")))
        .thenReturn(List.of(allowed, denied));
    when(assetGroupMapper.toAssetGroupOutput(allowed)).thenReturn(allowedOutput);
    when(assetGroupMapper.toAssetGroupOutput(denied)).thenReturn(deniedOutput);

    List<AssetGroupOutput> result = scopeService.getScopeAssetGroups(WORKFLOW_ID);

    assertThat(result).containsExactly(allowedOutput, deniedOutput);
  }

  @Test
  @DisplayName("Scope asset groups by IDs - IDs outside the workflow scope rules are ignored")
  void givenIdsOutsideScopeRules_whenFindingScopeAssetGroups_thenOnlyScopedIdsAreResolved() {
    AssetGroup allowed = new AssetGroup();
    allowed.setId("group-1");
    AssetGroupOutput allowedOutput = AssetGroupOutput.builder().id("group-1").build();

    when(workflowScopeRuleRepository.findAllByWorkflowId(WORKFLOW_ID))
        .thenReturn(List.of(allowlistRule(ScopeRuleValueType.ASSET_GROUP_ID, "group-1")));
    when(assetGroupService.assetGroups(List.of("group-1"))).thenReturn(List.of(allowed));
    when(assetGroupMapper.toAssetGroupOutput(allowed)).thenReturn(allowedOutput);

    List<AssetGroupOutput> result =
        scopeService.getScopeAssetGroupsByIds(WORKFLOW_ID, List.of("group-1", "group-9"));

    assertThat(result).containsExactly(allowedOutput);
  }
}
