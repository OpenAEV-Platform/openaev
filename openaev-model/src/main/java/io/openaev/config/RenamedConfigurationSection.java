package io.openaev.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * A renamed configuration section that keeps its former name as an alias.
 *
 * <p>Property sources are read in Spring priority order (command line, environment variables,
 * configuration files, packaged {@code application.properties}): the first source setting a key
 * under either name decides, and the new name wins inside a same source. A key is set as soon as a
 * source defines it, empty values included. Names are matched with Spring relaxed binding, so
 * {@code MINIO_ACCESS_KEY}, {@code MINIO_ACCESS-KEY} and {@code minio.access-key} are the same key.
 */
public class RenamedConfigurationSection {

  private final ConfigurableEnvironment environment;
  private final String prefix;
  private final String legacyPrefix;

  public RenamedConfigurationSection(
      ConfigurableEnvironment environment, String prefix, String legacyPrefix) {
    this.environment = environment;
    this.prefix = prefix;
    this.legacyPrefix = legacyPrefix;
  }

  /**
   * Former names read (used), former names hidden by a new name (ignored), new names hidden by a
   * former name of higher priority (shadowed). Property names only, never values. A new name set to
   * the value of the packaged application.properties is not reported as shadowed.
   */
  public record Deprecations(List<String> used, List<String> ignored, List<String> shadowed) {}

  /** The name, new or former, a key is read from. */
  public String resolve(String key) {
    ConfigurationPropertyName name = name(prefix, key);
    ConfigurationPropertyName legacyName = name(legacyPrefix, key);
    for (ConfigurationPropertySource source : sources()) {
      if (source.getConfigurationProperty(name) != null) {
        return name.toString();
      }
      if (source.getConfigurationProperty(legacyName) != null) {
        return legacyName.toString();
      }
    }
    return name.toString();
  }

  /** The value of a key, bound like {@code @ConfigurationProperties} does. */
  public <T> T get(String key, Class<T> type, T defaultValue) {
    return Binder.get(environment)
        .bind(ConfigurationPropertyName.of(resolve(key)), Bindable.of(type))
        .orElse(defaultValue);
  }

  public Deprecations deprecations(List<String> keys) {
    List<String> used = new ArrayList<>();
    List<String> ignored = new ArrayList<>();
    List<String> shadowed = new ArrayList<>();
    for (String key : keys) {
      ConfigurationPropertyName name = name(prefix, key);
      ConfigurationPropertyName legacyName = name(legacyPrefix, key);
      if (resolve(key).equals(legacyName.toString())) {
        used.add(legacyName.toString());
        // The packaged application.properties sets every new name. The release archive ships a copy
        // of it next to the jar: only a value that differs from the packaged one is a user setting.
        Object packagedValue = packagedValue(name);
        if (sources().stream()
            .anyMatch(
                s ->
                    isSet(s, name)
                        && !Objects.equals(
                            String.valueOf(s.getConfigurationProperty(name).getValue()),
                            String.valueOf(packagedValue)))) {
          shadowed.add(name.toString());
        }
      } else if (sources().stream().anyMatch(s -> isSet(s, legacyName))) {
        ignored.add(legacyName.toString());
      }
    }
    return new Deprecations(used, ignored, shadowed);
  }

  // The sources the Binder reads, in the same order
  private List<ConfigurationPropertySource> sources() {
    return StreamSupport.stream(ConfigurationPropertySources.get(environment).spliterator(), false)
        .toList();
  }

  private static boolean isSet(ConfigurationPropertySource source, ConfigurationPropertyName name) {
    return source.getConfigurationProperty(name) != null;
  }

  private Object packagedValue(ConfigurationPropertyName name) {
    return sources().stream()
        .filter(s -> isPackaged(s) && isSet(s, name))
        .map(s -> s.getConfigurationProperty(name).getValue())
        .findFirst()
        .orElse(null);
  }

  private static boolean isPackaged(ConfigurationPropertySource source) {
    return source.getUnderlyingSource() instanceof PropertySource<?> s
        && s.getName().contains("class path resource [application.properties]");
  }

  private static ConfigurationPropertyName name(String prefix, String key) {
    return ConfigurationPropertyName.of(prefix + "." + key);
  }
}
