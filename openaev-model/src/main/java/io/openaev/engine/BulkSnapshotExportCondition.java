package io.openaev.engine;

import java.util.Arrays;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers the bulk snapshot handler beans only when the preview feature is enabled.
 *
 * <p>Handlers live in this module and {@code PreviewFeatureService} lives in {@code openaev-api},
 * so the flag list is read straight off the {@link Environment}. Parsing mirrors {@code
 * PlatformSettingsService}, including the wildcard and the legacy {@code openbas.} key, so this
 * predicate and the platform settings can never disagree.
 *
 * <p>Gating bean registration rather than branching inside {@code fetch(...)} is what makes the
 * feature free when off: no bean means no model, hence no index, no mapping, no {@code
 * indexing_status} row and no storage.
 */
public class BulkSnapshotExportCondition implements Condition {

  public static final String FEATURE_NAME = "BULK_SNAPSHOT_EXPORT";

  private static final String WILDCARD = "*";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return isBulkSnapshotExportEnabled(context.getEnvironment());
  }

  /** The predicate, reusable outside a {@link Condition} and testable without a Spring context. */
  public static boolean isBulkSnapshotExportEnabled(Environment env) {
    String enabledDevFeatures =
        env.getProperty(
            "openbas.enabled-dev-features", env.getProperty("openaev.enabled-dev-features", ""));
    return Arrays.stream(enabledDevFeatures.split(","))
        .map(String::strip)
        .anyMatch(feature -> WILDCARD.equals(feature) || FEATURE_NAME.equalsIgnoreCase(feature));
  }
}
