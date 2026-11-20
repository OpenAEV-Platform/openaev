package io.openaev.api.graph;

import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The exploration graph preview feature ({@link PreviewFeature#EXPLORATION_GRAPH}, off by default).
 * While it is off, every route of the feature (graph slices, asset links, OpenCTI / OpenCRQ
 * counterparts) answers a 404, as if it did not exist.
 */
@Component
@RequiredArgsConstructor
public class GraphFeature {

  private final PreviewFeatureService previewFeatureService;

  public boolean isEnabled() {
    return previewFeatureService.isFeatureEnabled(PreviewFeature.EXPLORATION_GRAPH);
  }

  public void require() {
    if (!isEnabled()) {
      throw new ElementNotFoundException("The exploration graph feature is disabled");
    }
  }
}
