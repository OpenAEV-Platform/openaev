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
   * The Autonomous Attack Path feature depends on the attack-path projection (for the live animated
   * view) and the chaining engine (for execution). We keep the requires-both check internally for
   * correctness even though, once {@code ATTACK_PATH} and {@code INJECT_CHAINING} ship enabled by
   * default, {@code AUTONOMOUS_ATTACK_PATH} is the single flag an operator has to toggle.
   */
  public boolean isAutonomousAttackPathEnabled() {
    return isFeatureEnabled(PreviewFeature.AUTONOMOUS_ATTACK_PATH)
        && isFeatureEnabled(PreviewFeature.ATTACK_PATH)
        && isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
  }
}
