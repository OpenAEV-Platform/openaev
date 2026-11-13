package io.openaev.api.catalog;

import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers every catalogue route with a 404 while the catalogue preview feature is off. */
@Component
@RequiredArgsConstructor
public class CatalogFeature {

  private final PreviewFeatureService previewFeatureService;

  public void require() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.CATALOG)) {
      throw new ElementNotFoundException("The catalogue feature is disabled");
    }
  }
}
