package io.openaev.utils.object;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ObjectNormalizationPolicy {

  public static final int DEPTH_LEVEL = 10;
  public static final Set<String> DIFF_SKIP_FIELDS = Set.of("type");

  private static final Set<String> GLOBAL_DENYLIST_FIELDS =
      Set.of("password", "secret", "token", "token_value", "api_key", "authorization");

  private static final Map<String, Set<String>> ENTITY_DENYLIST_FIELDS =
      Map.of(
          "user", Set.of("user_password", "access_token", "refresh_token"),
          "organization", Set.of("organization_secret"));

  private static final Map<String, Set<String>> ENTITY_ALLOWLIST_FIELDS =
      Map.of(
          "audit_event",
          Set.of(
              "event_type",
              "event_scope",
              "resource_type",
              "entity_id",
              "entity_name",
              "changes",
              "timestamp"));

  private static final String TRUNCATED_SUFFIX = "...[TRUNCATED]";
  private static final String REDACTED_VALUE = "[REDACTED]";

  @Value("${openaev.audit.normalization.max-event-size-bytes:32768}")
  private int maxEventSizeBytes;

  @Value("${openaev.audit.normalization.max-string-bytes:1024}")
  private int maxStringBytes;

  @Value("${openaev.audit.normalization.truncation.preview-bytes:2048}")
  private int truncationPreviewBytes;

  @Value("${openaev.audit.normalization.skip-on-high-load:true}")
  private boolean skipOnHighLoad;

  @Value("${openaev.audit.normalization.skip-all:false}")
  private boolean skipAllNormalization;

  @Value("${openaev.audit.normalization.max-process-cpu-load:0.90}")
  private double maxProcessCpuLoad;

  @Value("${openaev.audit.normalization.max-heap-usage-ratio:0.90}")
  private double maxHeapUsageRatio;

  public int maxEventSizeBytes() {
    return maxEventSizeBytes;
  }

  public int maxStringBytes() {
    return maxStringBytes;
  }

  public int truncationPreviewBytes() {
    return truncationPreviewBytes;
  }

  public boolean skipOnHighLoad() {
    return skipOnHighLoad;
  }

  public boolean skipAllNormalization() {
    return skipAllNormalization;
  }

  public double maxProcessCpuLoad() {
    return maxProcessCpuLoad;
  }

  public double maxHeapUsageRatio() {
    return maxHeapUsageRatio;
  }

  public String truncatedSuffix() {
    return TRUNCATED_SUFFIX;
  }

  public String redactedValue() {
    return REDACTED_VALUE;
  }

  public String normalizeEntityType(String entityType) {
    if (entityType == null || entityType.isBlank()) {
      return "default";
    }
    return entityType.toLowerCase(Locale.ROOT);
  }

  public Set<String> allowlistForEntity(String entityType) {
    return ENTITY_ALLOWLIST_FIELDS.get(normalizeEntityType(entityType));
  }

  public Set<String> denylistForEntity(String entityType) {
    return ENTITY_DENYLIST_FIELDS.getOrDefault(normalizeEntityType(entityType), Set.of());
  }

  public boolean isGloballyDeniedField(String fieldName) {
    return GLOBAL_DENYLIST_FIELDS.contains(fieldName.toLowerCase(Locale.ROOT));
  }
}
