package io.openaev.service.attackpath;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.AssetType;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.expectation.ExpectationType;
import io.openaev.service.attackpath.dto.AttackPathAlertDTO;
import io.openaev.service.attackpath.dto.AttackPathSecurityPlatformDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves security-platform rows of one execution from the attack-path collector snapshot table.
 */
@Component
@RequiredArgsConstructor
public class AttackPathSecurityPlatformResolver {

  private static final String SUCCESS = BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name();
  private static final String PARTIAL = BaseInjectExpectation.EXPECTATION_STATUS.PARTIAL.name();
  private static final String PENDING = BaseInjectExpectation.EXPECTATION_STATUS.PENDING.name();
  private static final String FAILED = BaseInjectExpectation.EXPECTATION_STATUS.FAILED.name();
  private static final String UNKNOWN = "UNKNOWN";
  private static final String PREVENTION = BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION.name();
  private static final String DETECTION = BaseInjectExpectation.EXPECTATION_TYPE.DETECTION.name();

  private static final String VULNERABILITY =
      BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY.name();

  private final AttackPathExecutionCollectorRepository executionCollectorRepository;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  public List<AttackPathSecurityPlatformDTO> resolve(String executionId, String tenantId) {
    if (!enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return List.of();
    }
    if (executionId == null || tenantId == null) {
      return List.of();
    }

    List<AttackPathExecutionCollector> rows =
        executionCollectorRepository.findByExecutionIdAndTenantId(executionId, tenantId);
    Map<String, AttackPathSecurityPlatformDTO> prevention = new LinkedHashMap<>();
    Map<String, AttackPathSecurityPlatformDTO> detection = new LinkedHashMap<>();
    Map<String, AttackPathSecurityPlatformDTO> vulnerability = new LinkedHashMap<>();
    for (AttackPathExecutionCollector row : rows) {
      String bucket = expectationBucket(row.getExpectationType());
      if (bucket == null) {
        continue;
      }
      AttackPathSecurityPlatformDTO dto = toDto(row, bucket);
      if (PREVENTION.equals(bucket)) {
        putPreferSpecificType(prevention, dto);
      } else if (DETECTION.equals(bucket)) {
        putPreferSpecificType(detection, dto);
      } else {
        putPreferSpecificType(vulnerability, dto);
      }
    }

    prevention.forEach(
        (key, prevented) -> {
          if (!SUCCESS.equals(prevented.status())) {
            return;
          }
          AttackPathSecurityPlatformDTO existing = detection.get(key);
          if (existing == null) {
            detection.put(
                key,
                new AttackPathSecurityPlatformDTO(
                    prevented.sourceId(),
                    prevented.sourceAssetId(),
                    prevented.platformType(),
                    prevented.platformName(),
                    DETECTION,
                    SUCCESS,
                    prevented.detectedAt(),
                    prevented.resultLabel(),
                    prevented.score(),
                    prevented.alertsCount(),
                    prevented.alerts()));
            return;
          }
          if (!SUCCESS.equals(existing.status())) {
            detection.put(
                key,
                new AttackPathSecurityPlatformDTO(
                    existing.sourceId(),
                    existing.sourceAssetId(),
                    existing.platformType(),
                    existing.platformName(),
                    DETECTION,
                    SUCCESS,
                    existing.detectedAt(),
                    existing.resultLabel(),
                    existing.score(),
                    existing.alertsCount(),
                    existing.alerts()));
          }
        });

    List<AttackPathSecurityPlatformDTO> out = new ArrayList<>();
    out.addAll(prevention.values());
    out.addAll(detection.values());
    out.addAll(vulnerability.values());
    return out;
  }

  private AttackPathSecurityPlatformDTO toDto(AttackPathExecutionCollector row, String bucket) {
    String platformType =
        row.getSourceType() != null && !row.getSourceType().isBlank()
            ? row.getSourceType()
            : AssetType.Values.SECURITY_PLATFORM_TYPE;
    List<AttackPathAlertDTO> alerts = parseAlerts(row.getAlerts());
    return new AttackPathSecurityPlatformDTO(
        row.getSourceId(),
        row.getSourceAssetId(),
        platformType,
        row.getSourceName(),
        bucket,
        toStatusId(row.getResultStatusLabel(), row.getExpectationType()),
        row.getDetectionTime(),
        row.getResultStatusLabel(),
        row.getResultScore(),
        alerts.size(),
        alerts);
  }

  private List<AttackPathAlertDTO> parseAlerts(JsonNode rawAlerts) {
    if (rawAlerts == null || !rawAlerts.isArray()) {
      return List.of();
    }
    List<AttackPathAlertDTO> alerts = new ArrayList<>();
    for (JsonNode node : rawAlerts) {
      if (!node.isObject()) {
        continue;
      }
      String id = textOrNull(node, "id");
      String title = textOrNull(node, "title");
      if (id == null || title == null) {
        continue;
      }
      alerts.add(
          new AttackPathAlertDTO(id, title, textOrNull(node, "date"), textOrNull(node, "link")));
    }
    return alerts;
  }

  private String textOrNull(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText(null);
    return text == null || text.isBlank() ? null : text;
  }

  private String expectationBucket(String expectationType) {
    if (expectationType == null) {
      return null;
    }
    return switch (expectationType) {
      case "PREVENTION" -> PREVENTION;
      case "DETECTION" -> DETECTION;
      case "VULNERABILITY" -> VULNERABILITY;
      default -> null;
    };
  }

  private String platformKey(AttackPathSecurityPlatformDTO dto) {
    if (dto.sourceId() != null) {
      return dto.sourceId();
    }
    // Legacy-read compatibility only: historical rows may have no sourceId.
    if (dto.platformName() == null) {
      return "unknown";
    }
    return (dto.platformType() == null ? "" : dto.platformType()) + "\0" + dto.platformName();
  }

  private void putPreferSpecificType(
      Map<String, AttackPathSecurityPlatformDTO> bucket, AttackPathSecurityPlatformDTO candidate) {
    String key = platformKey(candidate);
    AttackPathSecurityPlatformDTO existing = bucket.get(key);
    if (existing == null) {
      bucket.put(key, candidate);
      return;
    }
    if (isGenericType(existing.platformType()) && !isGenericType(candidate.platformType())) {
      bucket.put(key, candidate);
    }
  }

  private boolean isGenericType(String type) {
    if (type == null) {
      return true;
    }
    String normalized = type.trim();
    if (normalized.isEmpty()) {
      return true;
    }
    return AssetType.Values.SECURITY_PLATFORM_TYPE.equalsIgnoreCase(normalized)
        || "SECURITY_PLATFORM_TYPE".equalsIgnoreCase(normalized)
        || "SECURITY_PLATFORM".equalsIgnoreCase(normalized);
  }

  private String toStatusId(String resultLabel, String expectationType) {
    if (resultLabel == null || expectationType == null) {
      return UNKNOWN;
    }
    ExpectationType type;
    try {
      type = ExpectationType.of(expectationType);
    } catch (Exception e) {
      return UNKNOWN;
    }
    // Tolerant match: collector outputs may differ from the canonical labels by case or
    // surrounding whitespace only, and those are still semantically the same result.
    String normalized = resultLabel.trim();
    if (normalized.equalsIgnoreCase(type.successLabel)) {
      return SUCCESS;
    }
    if (normalized.equalsIgnoreCase(type.partialLabel)) {
      return PARTIAL;
    }
    if (normalized.equalsIgnoreCase(type.pendingLabel)) {
      return PENDING;
    }
    if (normalized.equalsIgnoreCase(type.failureLabel)) {
      return FAILED;
    }
    return UNKNOWN;
  }
}
