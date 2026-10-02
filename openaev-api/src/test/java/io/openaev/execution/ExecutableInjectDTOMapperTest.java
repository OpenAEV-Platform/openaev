package io.openaev.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import java.util.List;
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

  private EndpointTargetOutput outputFor(Endpoint endpoint) {
    return EndpointTargetOutput.builder().id(endpoint.getId()).build();
  }

  private Inject inject() {
    Inject inject = new Inject();
    inject.setId("inject-1");
    inject.setTenant(new Tenant("tenant-1"));
    return inject;
  }

  private ExecutableInject executableInject(Inject inject, Endpoint... assets) {
    return new ExecutableInject(
        true, false, inject, List.of(), List.of(assets), List.of(), List.of(), List.of());
  }

  @Test
  @DisplayName(
      "a restricted asset present in executableInject.getAssets() is absent from the built DTO "
          + "when the cached assetsToExecute already excludes it (executor pre-cache path)")
  void given_cachedFilteredAssets_should_excludeRestrictedAssetFromDTO() {
    Endpoint endpointGreen = endpoint("endpoint-green");
    Endpoint endpointRed = endpoint("endpoint-red");
    Inject inject = inject();
    ExecutableInject executableInject = executableInject(inject, endpointGreen, endpointRed);
    // Simulates InjectsExecutionJob.executeInject() having already called
    // resolveAllAssetsToExecute() and cacheAssetsToExecute(...) before dispatch - the restricted
    // asset is already gone from the cached list.
    executableInject.cacheAssetsToExecute(List.of(new AssetToExecute(endpointGreen)));
    when(endpointMapper.toEndpointTargetOutput(endpointGreen)).thenReturn(outputFor(endpointGreen));

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
    ExecutableInject executableInject = executableInject(inject, endpointGreen, endpointRed);
    // Nothing cached this time (executableInject.getAssetsToExecute() is null) - a direct caller
    // (e.g. atomic testing, chaining) that doesn't pre-resolve, per AbstractTechnicalBehavior's own
    // fallback comment.
    when(injectService.resolveAllAssetsToExecute(inject))
        .thenReturn(List.of(new AssetToExecute(endpointGreen)));
    when(endpointMapper.toEndpointTargetOutput(endpointGreen)).thenReturn(outputFor(endpointGreen));

    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactly("endpoint-green");
    verify(injectService).resolveAllAssetsToExecute(inject);
    verify(endpointMapper, never()).toEndpointTargetOutput(endpointRed);
  }

  @Test
  @DisplayName("unmarked / non-restricted assets are unaffected: both are present in the DTO")
  void given_noRestriction_should_includeAllAssets() {
    Endpoint endpointA = endpoint("endpoint-a");
    Endpoint endpointB = endpoint("endpoint-b");
    Inject inject = inject();
    ExecutableInject executableInject = executableInject(inject, endpointA, endpointB);
    executableInject.cacheAssetsToExecute(
        List.of(new AssetToExecute(endpointA), new AssetToExecute(endpointB)));
    when(endpointMapper.toEndpointTargetOutput(any(Endpoint.class)))
        .thenAnswer(invocation -> outputFor(invocation.getArgument(0)));

    ExecutableInjectDTO dto = mapper.toExecutableInjectDTO(executableInject, null);

    assertThat(dto.getAssets())
        .extracting(EndpointTargetOutput::getId)
        .containsExactlyInAnyOrder("endpoint-a", "endpoint-b");
  }
}
