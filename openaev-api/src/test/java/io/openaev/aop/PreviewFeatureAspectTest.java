package io.openaev.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.annotation.NoTenantScope;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@DisplayName("Preview feature gate declared on a controller")
class PreviewFeatureAspectTest {

  @RequirePreviewFeature(PreviewFeature.EXPLORATION_GRAPH)
  @NoTenantScope
  static class GatedController {
    @GetMapping("/read")
    @Transactional
    public String read() {
      return "read";
    }

    @PostMapping("/write")
    @Transactional
    public String write() {
      return "write";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handle() {
      return "handled";
    }
  }

  @NoTenantScope
  static class OpenController {
    @GetMapping("/open")
    @Transactional
    public String open() {
      return "open";
    }
  }

  private PreviewFeatureService previewFeatures;
  private GatedController gated;
  private OpenController open;

  @BeforeEach
  void setUp() {
    previewFeatures = mock(PreviewFeatureService.class);
    PreviewFeatureAspect aspect = new PreviewFeatureAspect(previewFeatures);
    gated = proxy(new GatedController(), aspect);
    open = proxy(new OpenController(), aspect);
  }

  private static <T> T proxy(T target, PreviewFeatureAspect aspect) {
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(aspect);
    return factory.getProxy();
  }

  @Test
  @DisplayName("given the feature off, every endpoint of the controller answers not found")
  void given_featureOff_should_refuseEveryEndpoint() {
    when(previewFeatures.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH)).thenReturn(false);

    assertThatThrownBy(gated::read).isInstanceOf(ElementNotFoundException.class);
    assertThatThrownBy(gated::write).isInstanceOf(ElementNotFoundException.class);
  }

  @Test
  @DisplayName("given the feature on, every endpoint of the controller is served")
  void given_featureOn_should_serveEveryEndpoint() {
    when(previewFeatures.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH)).thenReturn(true);

    assertThat(gated.read()).isEqualTo("read");
    assertThat(gated.write()).isEqualTo("write");
  }

  @Test
  @DisplayName("given the feature off, a method that is not an endpoint is left alone")
  void given_featureOff_should_leaveNonEndpointsAlone() {
    when(previewFeatures.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH)).thenReturn(false);

    assertThat(gated.handle()).isEqualTo("handled");
  }

  @Test
  @DisplayName("a controller without the annotation is never gated")
  void given_noAnnotation_should_neverGate() {
    when(previewFeatures.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH)).thenReturn(false);

    assertThat(open.open()).isEqualTo("open");
  }
}
