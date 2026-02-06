package io.openaev.service;

import static io.openaev.rest.settings.PreviewFeature.FEATURE_FLAG_ALL;

import io.openaev.rest.settings.PreviewFeature;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreviewFeatureService {
  private final PlatformSettingsService platformSettingsService;

  @Cacheable("global")
  public boolean isFeatureEnabled(PreviewFeature feature) {
    List<PreviewFeature> enabledFeatures =
        platformSettingsService.findSettings().getEnabledDevFeatures();
    return enabledFeatures.contains(FEATURE_FLAG_ALL) || enabledFeatures.contains(feature);
  }
}
