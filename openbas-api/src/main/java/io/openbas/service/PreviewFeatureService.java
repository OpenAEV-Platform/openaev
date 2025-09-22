package io.openbas.service;

import io.openbas.rest.settings.PreviewFeature;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreviewFeatureService {
  private final PlatformSettingsService platformSettingsService;

  public boolean isFeatureEnabled(PreviewFeature feature) {
    List<PreviewFeature> enabledFeatures =
        platformSettingsService.findSettings().getEnabledDevFeatures();
    return enabledFeatures.contains(feature);
  }
}
