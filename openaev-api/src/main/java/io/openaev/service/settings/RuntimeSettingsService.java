package io.openaev.service.settings;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Service;

/**
 * Resolves the {@link RuntimeSetting}s: the environment variable wins and locks the setting, else
 * the value an administrator stored, else the configured default. Every read goes to the database,
 * so a change made in the UI applies at the next use, without a restart.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuntimeSettingsService {

  /** Where the value in force comes from. */
  public enum Source {
    ENV,
    UI,
    DEFAULT;

    public String label() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** A setting as the API exposes it. */
  public record Resolved(RuntimeSetting setting, String value, Source source, String envVar) {

    public boolean locked() {
      return source == Source.ENV;
    }
  }

  private final SettingRepository settingRepository;
  private final Environment environment;

  /** The value in force and its source. */
  public Resolved resolve(RuntimeSetting setting) {
    Optional<String> env = environmentValue(setting);
    if (env.isPresent()) {
      String value = setting.normalize(env.get());
      if (value != null) {
        return new Resolved(setting, value, Source.ENV, envVarName(setting));
      }
      log.warn(
          "Environment variable {} = {} is not valid for {}, it is ignored",
          envVarName(setting),
          env.get(),
          setting.key());
    }
    Optional<String> stored = storedValue(setting);
    if (stored.isPresent()) {
      return new Resolved(setting, stored.get(), Source.UI, envVarName(setting));
    }
    return new Resolved(setting, configuredDefault(setting), Source.DEFAULT, envVarName(setting));
  }

  public List<Resolved> resolveAll() {
    return Arrays.stream(RuntimeSetting.values()).map(this::resolve).toList();
  }

  public int intValue(RuntimeSetting setting) {
    return Integer.parseInt(resolve(setting).value());
  }

  public boolean booleanValue(RuntimeSetting setting) {
    return Boolean.parseBoolean(resolve(setting).value());
  }

  /**
   * Stores the values an administrator set. A key unknown, a value invalid for its setting or a
   * setting pinned by its environment variable refuses the whole write, nothing stored.
   */
  public void update(Map<String, Object> values) {
    if (values == null || values.isEmpty()) {
      throw new BadRequestException("No setting to update");
    }
    Map<RuntimeSetting, String> toStore = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    values.forEach(
        (key, raw) -> {
          RuntimeSetting setting = RuntimeSetting.fromKey(key);
          if (setting == null) {
            errors.add(key + ": unknown setting");
            return;
          }
          if (resolve(setting).locked()) {
            errors.add(
                key + ": set by the environment variable " + envVarName(setting) + ", read-only");
            return;
          }
          String value = setting.normalize(raw == null ? null : String.valueOf(raw));
          if (value == null) {
            errors.add(
                setting.type() == RuntimeSetting.Type.BOOLEAN
                    ? key + ": must be true or false"
                    : key
                        + ": must be an integer between "
                        + setting.min()
                        + " and "
                        + setting.max());
            return;
          }
          toStore.put(setting, value);
        });
    if (!errors.isEmpty()) {
      throw new BadRequestException(String.join("; ", errors));
    }
    toStore.forEach(
        (setting, value) -> {
          Setting row =
              settingRepository
                  .findByKeyAndTenantIsNull(setting.key())
                  .orElseGet(() -> new Setting(setting.key(), value));
          row.setValue(value);
          settingRepository.save(row);
        });
  }

  // -- Sources --

  /** The value an administrator stored, when it is valid. */
  Optional<String> storedValue(RuntimeSetting setting) {
    return settingRepository
        .findByKeyAndTenantIsNull(setting.key())
        .map(Setting::getValue)
        .filter(value -> value != null && !value.isBlank())
        .flatMap(
            value -> {
              String normalized = setting.normalize(value);
              if (normalized == null) {
                log.warn(
                    "Setting {} = {} is not valid, the configured value is used",
                    setting.key(),
                    value);
              }
              return Optional.ofNullable(normalized);
            });
  }

  /** The value the environment sets, read from the environment itself, never from a default. */
  Optional<String> environmentValue(RuntimeSetting setting) {
    PropertySource<?> source = systemEnvironment();
    if (source == null) {
      return Optional.empty();
    }
    for (String name : envVarCandidates(setting)) {
      Object value = source.getProperty(name);
      if (value != null) {
        return Optional.of(String.valueOf(value));
      }
    }
    return Optional.empty();
  }

  /** The configured default: every property source but the environment, else the fallback. */
  String configuredDefault(RuntimeSetting setting) {
    if (environment instanceof ConfigurableEnvironment configurable) {
      for (PropertySource<?> source : configurable.getPropertySources()) {
        if (isSystemEnvironment(source)) {
          continue;
        }
        Object value = source.getProperty(setting.property());
        if (value != null) {
          String normalized = setting.normalize(String.valueOf(value));
          if (normalized != null) {
            return normalized;
          }
        }
      }
    }
    return setting.fallback();
  }

  /** The variable name a deployment sets, the dashed form included (Docker allows it). */
  public String envVarName(RuntimeSetting setting) {
    PropertySource<?> source = systemEnvironment();
    if (source != null) {
      for (String name : envVarCandidates(setting)) {
        if (source.getProperty(name) != null) {
          return name;
        }
      }
    }
    return setting.envVar();
  }

  private static List<String> envVarCandidates(RuntimeSetting setting) {
    String upper = setting.property().toUpperCase(Locale.ROOT);
    return List.of(
        setting.envVar(), upper.replace('.', '_'), upper.replace('.', '_').replace("-", ""));
  }

  private PropertySource<?> systemEnvironment() {
    if (environment instanceof ConfigurableEnvironment configurable) {
      return configurable
          .getPropertySources()
          .get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    }
    return null;
  }

  private static boolean isSystemEnvironment(PropertySource<?> source) {
    return StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(source.getName());
  }
}
