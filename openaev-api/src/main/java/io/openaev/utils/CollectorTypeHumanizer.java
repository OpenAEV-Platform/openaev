package io.openaev.utils;

import io.openaev.database.model.SecurityPlatform;
import java.util.Locale;
import java.util.Map;

/**
 * Maps legacy collector type names (e.g. {@code openaev_crowdstrike}) to human-readable security
 * platform names and types. Detection remediations used to be keyed to collector types; they are
 * now keyed to {@link SecurityPlatform} assets, and this mapping is the single source of truth used
 * both by the re-keying migration and by the V1 importer when old exports still carry collector
 * type names.
 */
public final class CollectorTypeHumanizer {

  /** A security platform (name, type) derived from a legacy collector type name. */
  public record HumanizedPlatform(String name, SecurityPlatform.SECURITY_PLATFORM_TYPE type) {}

  private static final String COLLECTOR_PREFIX = "openaev_";

  private static final Map<String, HumanizedPlatform> KNOWN_COLLECTOR_TYPES =
      Map.of(
          "openaev_crowdstrike",
          new HumanizedPlatform("CrowdStrike Falcon", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
          "openaev_splunk_es",
          new HumanizedPlatform(
              "Splunk Enterprise Security", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM),
          "openaev_microsoft_defender",
          new HumanizedPlatform("Microsoft Defender", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR),
          "openaev_microsoft_sentinel",
          new HumanizedPlatform(
              "Microsoft Sentinel", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM));

  private CollectorTypeHumanizer() {}

  /**
   * Derives a security platform (name, type) from a legacy collector type name. Known collector
   * types get their vendor name; unknown ones are humanized (prefix stripped, underscores to
   * spaces, title case) and default to SIEM.
   *
   * @param collectorTypeName legacy collector type name (e.g. {@code openaev_crowdstrike})
   * @return the humanized platform name and type
   */
  public static HumanizedPlatform humanize(String collectorTypeName) {
    String normalized = collectorTypeName == null ? "" : collectorTypeName.trim();
    if (normalized.isEmpty()) {
      // asset_name is NOT BLANK: never derive an empty platform name from a missing type.
      return new HumanizedPlatform("Unknown", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
    }
    HumanizedPlatform known = KNOWN_COLLECTOR_TYPES.get(normalized.toLowerCase(Locale.ROOT));
    if (known != null) {
      return known;
    }
    String stripped =
        normalized.toLowerCase(Locale.ROOT).startsWith(COLLECTOR_PREFIX)
            ? normalized.substring(COLLECTOR_PREFIX.length())
            : normalized;
    String[] words = stripped.replace('_', ' ').trim().split("\\s+");
    StringBuilder titleCased = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (!titleCased.isEmpty()) {
        titleCased.append(' ');
      }
      titleCased
          .append(Character.toUpperCase(word.charAt(0)))
          .append(word.substring(1).toLowerCase(Locale.ROOT));
    }
    String name = titleCased.isEmpty() ? normalized : titleCased.toString();
    return new HumanizedPlatform(name, SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
  }
}
