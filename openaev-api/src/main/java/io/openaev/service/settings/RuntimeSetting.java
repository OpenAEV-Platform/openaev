package io.openaev.service.settings;

import static io.openaev.scheduler.jobs.InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;

import java.util.Locale;

/**
 * A platform setting an administrator may change at runtime in Settings &gt; Parameters, that a
 * deployment may also pin with its environment variable.
 *
 * <p>Resolution order: the environment variable when it is set (the UI field is then read-only and
 * a write is refused), else the value an administrator stored (a {@code tenant_id IS NULL} row of
 * {@code parameters} under {@link #key()}), else the configured default ({@code
 * application.properties} or any non-environment property source).
 */
public enum RuntimeSetting {
  INJECT_EXECUTION_THRESHOLD_MINUTES(
      "inject_execution_threshold_minutes",
      "inject.execution.threshold.minutes",
      Type.INTEGER,
      String.valueOf(DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES),
      1,
      1440,
      false),
  AGENT_MAX_SIMULTANEOUS_JOBS(
      "agent_max_simultaneous_jobs",
      "executor.openaev.agent.max-simultaneous-jobs",
      Type.INTEGER,
      "5",
      1,
      100,
      false),
  AGENT_QUEUE_THRESHOLD(
      "agent_queue_threshold", "openaev.agent.queue-threshold", Type.INTEGER, "0", 0, 10000, false),
  AGENT_AUTO_UPDATE_ENABLED(
      "agent_auto_update_enabled",
      "executor.openaev.agent.auto-update-enabled",
      Type.BOOLEAN,
      "true",
      0,
      0,
      true);

  public enum Type {
    INTEGER,
    BOOLEAN
  }

  private final String key;
  private final String property;
  private final Type type;
  private final String fallback;
  private final int min;
  private final int max;
  private final boolean enterpriseOnly;

  RuntimeSetting(
      String key,
      String property,
      Type type,
      String fallback,
      int min,
      int max,
      boolean enterpriseOnly) {
    this.key = key;
    this.property = property;
    this.type = type;
    this.fallback = fallback;
    this.min = min;
    this.max = max;
    this.enterpriseOnly = enterpriseOnly;
  }

  /** The {@code parameters} key and the JSON field name. */
  public String key() {
    return key;
  }

  /** The Spring property the deployment configures. */
  public String property() {
    return property;
  }

  /** The canonical environment variable of {@link #property()} (Spring relaxed binding). */
  public String envVar() {
    return property.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
  }

  public Type type() {
    return type;
  }

  /** The default when no property source defines {@link #property()}. */
  public String fallback() {
    return fallback;
  }

  public int min() {
    return min;
  }

  public int max() {
    return max;
  }

  /** Only effective with a valid Enterprise Edition license. */
  public boolean enterpriseOnly() {
    return enterpriseOnly;
  }

  /**
   * The value normalised to its type, or {@code null} when it is not valid for this setting (not an
   * integer, out of range, not a boolean).
   */
  public String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    if (type == Type.BOOLEAN) {
      if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
        return value.toLowerCase(Locale.ROOT);
      }
      return null;
    }
    try {
      int number = Integer.parseInt(value);
      return number < min || number > max ? null : String.valueOf(number);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static RuntimeSetting fromKey(String key) {
    for (RuntimeSetting setting : values()) {
      if (setting.key.equals(key)) {
        return setting;
      }
    }
    return null;
  }
}
