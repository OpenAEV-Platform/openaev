package io.openaev.injectors.openaev;

import static io.openaev.utils.ExpectationUtils.OAEV_IMPLANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injection;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.InjectorContext;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAEVImplantExecutorAssetsCacheTest {

  private static final String INJECT_ID = "inject-1";

  @Mock private InjectorContext injectorContext;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private InjectService injectService;

  private OpenAEVImplantExecutor implantExecutor;
  private Inject inject;
  private ExecutableInject executableInject;

  @BeforeEach
  void setUp() {
    implantExecutor =
        new OpenAEVImplantExecutor(injectorContext, injectExpectationService, injectService);
    inject = new Inject();
    inject.setId(INJECT_ID);
    Injection injection = mock(Injection.class);
    lenient().when(injection.getInject()).thenReturn(inject);
    executableInject = new ExecutableInject(false, false, injection, List.of());
  }

  private static AssetToExecute target(String assetId) {
    Asset asset = new Asset();
    asset.setId(assetId);
    return new AssetToExecute(asset);
  }

  @Nested
  @DisplayName("process - target resolution")
  class TargetResolutionTests {

    @Test
    @DisplayName(
        "given targets already resolved by the dispatch, should reuse them without reloading the"
            + " inject or resolving the targets again")
    void given_targetsCachedByDispatch_should_reuseThemWithoutResolvingAgain() throws Exception {
      // -- ARRANGE --
      executableInject.cacheAssetsToExecute(List.of(target("asset-green")));
      Execution execution = new Execution(false);

      // -- ACT --
      implantExecutor.process(execution, executableInject);

      // -- ASSERT --
      verify(injectService, never()).inject(any());
      verify(injectService, never()).resolveAllAssetsToExecute(any());
      assertThat(execution.getTraces()).isEmpty();
      verify(injectExpectationService).computeAndSaveExpectations(executableInject, OAEV_IMPLANT);
    }

    @Test
    @DisplayName(
        "given nothing resolved by the caller, should resolve the targets once and cache them for"
            + " the expectations")
    void given_nothingCached_should_resolveOnceAndCache() throws Exception {
      // -- ARRANGE --
      Inject reloaded = new Inject();
      reloaded.setId(INJECT_ID);
      List<AssetToExecute> resolved = List.of(target("asset-green"));
      when(injectService.inject(INJECT_ID)).thenReturn(reloaded);
      when(injectService.resolveAllAssetsToExecute(reloaded)).thenReturn(resolved);
      Execution execution = new Execution(false);

      // -- ACT --
      implantExecutor.process(execution, executableInject);

      // -- ASSERT --
      verify(injectService, times(1)).resolveAllAssetsToExecute(reloaded);
      assertThat(executableInject.getAssetsToExecute()).isEqualTo(resolved);
      assertThat(execution.getTraces()).isEmpty();
    }

    @Test
    @DisplayName("given an empty cached resolution, should trace that no asset can be targeted")
    void given_emptyCachedResolution_should_traceNoAssetToExecute() throws Exception {
      // -- ARRANGE --
      executableInject.cacheAssetsToExecute(List.of());
      Execution execution = new Execution(false);

      // -- ACT --
      implantExecutor.process(execution, executableInject);

      // -- ASSERT --
      verify(injectService, never()).resolveAllAssetsToExecute(any());
      assertThat(execution.getTraces())
          .singleElement()
          .satisfies(trace -> assertThat(trace.getMessage()).startsWith("Found 0 asset"));
    }
  }
}
