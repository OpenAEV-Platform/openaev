package io.openaev.service.attackpath;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.attackpath.dto.AttackPathAlertDTO;
import io.openaev.service.attackpath.dto.AttackPathSecurityPlatformDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves, for one attack-path execution, the security platforms that acted (prevention /
 * detection) and their linked alerts (requirement A1).
 *
 * <p>Live resolution from the execution's inject expectations, scoped to its agent (or asset when
 * the execution has no agent), mirroring how the drawer already resolves ATT&amp;CK techniques and
 * detection remediations live. This is the accepted MVP trade-off: expectation results are more
 * volatile than a frozen snapshot, so a past run renders today's platform verdicts.
 * Enterprise-gated like the detection remediations.
 *
 * <p>Status per platform is the expectation's aggregate status ({@link
 * BaseInjectExpectation#getResponse()}), attributed to each platform (source) in that expectation's
 * results: a per-result score carries no expected score, and this matches the drawer's existing
 * semantics (it lists platforms off the aggregate status). {@code prevented ⇒ detected} is applied
 * once here: a platform that prevented is also surfaced as having detected.
 */
@Component
@RequiredArgsConstructor
public class AttackPathSecurityPlatformResolver {

  private static final String PREVENTION = "prevention";
  private static final String DETECTION = "detection";
  private static final String SUCCESS = BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name();

  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectExpectationTraceRepository injectExpectationTraceRepository;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  public List<AttackPathSecurityPlatformDTO> resolve(
      String injectId, String agentId, String targetAssetId) {
    // Enterprise-gated, like the detection remediations: an inactive licence yields an empty list.
    if (!enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return List.of();
    }
    // Not-yet-committed run (no inject on the step) or a target with neither agent nor asset (raw /
    // manual): nothing to scope by, so nothing to show.
    if (injectId == null || (agentId == null && targetAssetId == null)) {
      return List.of();
    }
    List<BaseInjectExpectation> expectations =
        agentId != null
            ? injectExpectationRepository.findAllByInjectAndAgent(injectId, agentId)
            : injectExpectationRepository.findAllByInjectAndAsset(injectId, targetAssetId);

    // bucket -> platformKey -> entry, so a platform appears once per bucket even across
    // expectations.
    Map<String, Map<String, AttackPathSecurityPlatformDTO>> byBucket = new LinkedHashMap<>();
    byBucket.put(PREVENTION, new LinkedHashMap<>());
    byBucket.put(DETECTION, new LinkedHashMap<>());

    for (BaseInjectExpectation expectation : expectations) {
      String bucket = bucketOf(expectation);
      if (bucket == null) {
        continue; // ignore manual / vulnerability / article / challenge expectations
      }
      String status = expectation.getResponse().name();
      for (InjectExpectationResult result : expectation.getResults()) {
        String platformKey = platformKey(result);
        if (platformKey == null) {
          continue; // a result with no source platform carries no security-platform signal
        }
        byBucket.get(bucket).putIfAbsent(platformKey, toDto(expectation, result, bucket, status));
      }
    }

    // prevented ⇒ detected: a platform that prevented is authoritatively also detected (you cannot
    // block what you did not see). Applied once, here. If the platform has no detection entry we
    // add
    // one (no detection expectation, so no alerts); if it has a contradictory non-SUCCESS one we
    // upgrade its status to SUCCESS, keeping its real alerts and date.
    Map<String, AttackPathSecurityPlatformDTO> detection = byBucket.get(DETECTION);
    byBucket
        .get(PREVENTION)
        .forEach(
            (platformKey, prevented) -> {
              if (!SUCCESS.equals(prevented.status())) {
                return;
              }
              AttackPathSecurityPlatformDTO existing = detection.get(platformKey);
              if (existing == null) {
                detection.put(
                    platformKey,
                    new AttackPathSecurityPlatformDTO(
                        prevented.platformType(),
                        prevented.platformName(),
                        DETECTION,
                        SUCCESS,
                        prevented.detectedAt(),
                        List.of()));
              } else if (!SUCCESS.equals(existing.status())) {
                detection.put(
                    platformKey,
                    new AttackPathSecurityPlatformDTO(
                        existing.platformType(),
                        existing.platformName(),
                        DETECTION,
                        SUCCESS,
                        existing.detectedAt(),
                        existing.alerts()));
              }
            });

    List<AttackPathSecurityPlatformDTO> out = new ArrayList<>();
    out.addAll(byBucket.get(PREVENTION).values());
    out.addAll(byBucket.get(DETECTION).values());
    return out;
  }

  private static String bucketOf(BaseInjectExpectation expectation) {
    return switch (expectation.getType()) {
      case PREVENTION -> PREVENTION;
      case DETECTION -> DETECTION;
      default -> null;
    };
  }

  /** The platform identity of a result: its source platform id, else its source name. */
  private static String platformKey(InjectExpectationResult result) {
    if (result.getSourceId() != null) {
      return result.getSourceId();
    }
    return result.getSourceName();
  }

  private AttackPathSecurityPlatformDTO toDto(
      BaseInjectExpectation expectation,
      InjectExpectationResult result,
      String bucket,
      String status) {
    // Prefer the real security platform (its EDR/XDR/SIEM type + name); fall back to the result's
    // frozen source fields when the platform entity cannot be resolved.
    String platformType = result.getSourceType();
    String platformName = result.getSourceName();
    if (result.getSourceId() != null) {
      var platform = securityPlatformRepository.findById(result.getSourceId()).orElse(null);
      if (platform != null) {
        platformType =
            platform.getSecurityPlatformType() == null
                ? platformType
                : platform.getSecurityPlatformType().name();
        platformName = platform.getName();
      }
    }
    List<AttackPathAlertDTO> alerts =
        result.getSourceId() == null
            ? List.of()
            : injectExpectationTraceRepository
                .findByExpectationAndSecurityPlatform(expectation.getId(), result.getSourceId())
                .stream()
                .map(
                    t ->
                        new AttackPathAlertDTO(
                            t.getId(),
                            t.getAlertName(),
                            t.getAlertDate() == null ? null : t.getAlertDate().toString(),
                            t.getAlertLink()))
                .toList();
    return new AttackPathSecurityPlatformDTO(
        platformType, platformName, bucket, status, result.getDate(), alerts);
  }
}
