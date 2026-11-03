package io.openaev.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@code ExecutableInjectDTOMapper} is the external-push dispatch path for non-agent connectors -
 * it must consume the marking-filtered {@code assetsToExecute}, never the original, unfiltered
 * {@code executableInject.getAssets()}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutableInjectDTOMapperTest {

  @Mock private io.openaev.utils.mapper.EndpointMapper endpointMapper;
  @Mock private io.openaev.utils.mapper.AssetGroupMapper assetGroupMapper;
  @Mock private InjectService injectService;

  @InjectMocks private ExecutableInjectDTOMapper mapper;

  private Endpoint endpoint(String id) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    return endpoint;
  }

  private EndpointTargetOutput outputForEndpoint(Endpoint endpoint) {
    return EndpointTargetOutput.builder().id(endpoint.getId()).assetGroups(Set.of()).build();
  }

  private EndpointTargetOutput outputForEndpoint(Endpoint endpoint, Set<AssetGroup> assetGroups) {
    return EndpointTargetOutput.builder()
        .id(endpoint.getId())
        .assetGroups(
            assetGroups.stream().map(this::outputForAssetGroup).collect(Collectors.toSet()))
        .build();
  }

  private AssetGroupSimple outputForAssetGroup(AssetGroup assetGroup) {
    return AssetGroupSimple.builder().id(assetGroup.getId()).build();
  }

  private Inject inject() {
    Inject inject = new Inject();
    inject.setId("inject-1");
    inject.setTenant(new Tenant("tenant-1"));
    return inject;
  }

  private ExecutableInject executableInject(Inject inject, List<Asset> assets) {
    return new ExecutableInject(
        true, false, inject, List.of(), assets, List.of(), List.of(), List.of());
  }

  private ExecutableInject executableInject(
      Inject inject, List<Asset> assets, List<AssetGroup> assetGroups) {
    return new ExecutableInject(
        true, false, inject, List.of(), assets, assetGroups, List.of(), List.of());
  }

  @Test
  @DisplayName(
      "a restricted asset present in executableInject.getAssets() is absent from the built DTO "
          + "when the cached assetsToExecute already excludes it (executor pre-cache path)")
  void given_cachedFilteredAssets_should_excludeRestrictedAssetFromDTO() {
    Endpoint endpointGreen = endpoint("endpoint-green");
    Endpoint endpointRed = endpoint("endpoint-red");
    Inject inject = inject();
    ExecutableInject executableInject =
        executableInject(inject, List.of(endpointGreen, endpointRed));
    // Simulates InjectsExecutionJob.executeInject() having already called
    // resolveAllAssetsToExecute() and cacheAssetsToExecute(...) before dispatch - the restricted
    // asset is already gone from the cached list.
    executableInject.cacheAssetsToExecute(List.of(new AssetToExecute(endpointGreen)));
    when(endpointMapper.toEndpointTargetOutput(eq(endpointGreen), any()))
        .thenReturn(outputForEndpoint(endpointGreen));

    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactly("endpoint-green");
    verifyNoInteractions(injectService);
  }

  @Test
  @DisplayName(
      "a restricted asset is absent from the built DTO via the fallback path too, for a direct "
          + "caller that never pre-cached assetsToExecute")
  void given_noCachedAssets_should_fallBackToResolveAllAssetsToExecute_andExcludeRestrictedAsset() {
    Endpoint endpointGreen = endpoint("endpoint-green");
    Endpoint endpointRed = endpoint("endpoint-red");
    Inject inject = inject();
    ExecutableInject executableInject =
        executableInject(inject, List.of(endpointGreen, endpointRed));
    // Nothing cached this time (executableInject.getAssetsToExecute() is null) - a direct caller
    // (e.g. atomic testing, chaining) that doesn't pre-resolve, per AbstractTechnicalBehavior's own
    // fallback comment.
    when(injectService.resolveAllAssetsToExecute(inject))
        .thenReturn(List.of(new AssetToExecute(endpointGreen)));
    when(endpointMapper.toEndpointTargetOutput(eq(endpointGreen), any()))
        .thenReturn(outputForEndpoint(endpointGreen));

    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactly("endpoint-green");
    verify(injectService).resolveAllAssetsToExecute(inject);
    verify(endpointMapper, never()).toEndpointTargetOutput(eq(endpointRed), any());
  }

  @Test
  @DisplayName("unmarked / non-restricted assets are unaffected: both are present in the DTO")
  void given_noRestriction_should_includeAllAssets() {
    Endpoint endpointA = endpoint("endpoint-a");
    Endpoint endpointB = endpoint("endpoint-b");
    Inject inject = inject();
    ExecutableInject executableInject = executableInject(inject, List.of(endpointA, endpointB));
    executableInject.cacheAssetsToExecute(
        List.of(new AssetToExecute(endpointA), new AssetToExecute(endpointB)));
    when(endpointMapper.toEndpointTargetOutput(any(Endpoint.class), any()))
        .thenAnswer(invocation -> outputForEndpoint(invocation.getArgument(0)));

    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactlyInAnyOrder("endpoint-a", "endpoint-b");
  }

  @Test
  @DisplayName(
      "each endpoint target carries the IDs of the inject's asset groups it was resolved from")
  void given_assetsResolvedFromAssetGroups_should_passTheirGroupIdsToTheTargets() {
    // Arrange
    Endpoint endpointInTwoGroups = endpoint("endpoint-two-groups");
    Endpoint directEndpoint = endpoint("endpoint-direct");
    AssetGroup groupA = new AssetGroup();
    groupA.setId("group-a");
    AssetGroup groupB = new AssetGroup();
    groupB.setId("group-b");
    Inject inject = inject();
    ExecutableInject executableInject =
        executableInject(
            inject, List.of(endpointInTwoGroups, directEndpoint), List.of(groupA, groupB));
    executableInject.cacheAssetsToExecute(
        List.of(
            new AssetToExecute(
                endpointInTwoGroups, false, new ArrayList<>(List.of(groupA, groupB))),
            new AssetToExecute(directEndpoint)));

    when(endpointMapper.toEndpointTargetOutput(endpointInTwoGroups, Set.of(groupA, groupB)))
        .thenReturn(outputForEndpoint(endpointInTwoGroups, Set.of(groupA, groupB)));
    when(endpointMapper.toEndpointTargetOutput(directEndpoint, Set.of()))
        .thenReturn(outputForEndpoint(directEndpoint));
    when(assetGroupMapper.toAssetGroupSimple(groupA)).thenReturn(outputForAssetGroup(groupA));
    when(assetGroupMapper.toAssetGroupSimple(groupB)).thenReturn(outputForAssetGroup(groupB));

    // Act
    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    // Assert

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactlyInAnyOrder("endpoint-two-groups", "endpoint-direct");

    assertThat(dto.getAssets())
        .extracting(
            target ->
                target.getAssetGroups().stream()
                    .map(AssetGroupSimple::getId)
                    .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(Set.of("group-a", "group-b"), Set.of());

    assertThat(dto.getAssetGroups())
        .extracting(AssetGroupSimple::getId)
        .containsExactlyInAnyOrder("group-a", "group-b");

    verify(endpointMapper).toEndpointTargetOutput(endpointInTwoGroups, Set.of(groupA, groupB));
    verify(endpointMapper).toEndpointTargetOutput(directEndpoint, Set.of());
    verify(assetGroupMapper).toAssetGroupSimple(groupA);
    verify(assetGroupMapper).toAssetGroupSimple(groupB);
  }
}
