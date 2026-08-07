package io.openaev.service;

import static io.openaev.rest.settings.PreviewFeature.FEATURE_FLAG_ALL;

import io.openaev.rest.settings.PreviewFeature;
import java.util.List;
import java.util.Optional;
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
        Optional.ofNullable(platformSettingsService.findSettings().getEnabledDevFeatures())
            .orElse(List.of());
    return enabledFeatures.contains(FEATURE_FLAG_ALL) || enabledFeatures.contains(feature);
  }

  /**
   * Autonomy is a launch-time MODE of a chained scenario, not a feature of its own: it is gated by
   * the same {@code INJECT_CHAINING} flag that turns on the chaining engine it drives. There is no
   * dedicated autonomous flag anymore - a tenant that has chaining has autonomous.
   */
  public boolean isAutonomousAttackPathEnabled() {
    return isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
  }
}
