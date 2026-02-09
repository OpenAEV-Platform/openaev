package io.openaev.service.settings;

import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.repository.SettingRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class SettingService {

  private final SettingRepository settingRepository;

  /** Retrieves a boolean setting value from the settings repository */
  public boolean getBoolean(SettingKeys key) {
    Objects.requireNonNull(key, "key must not be null");
    return settingRepository
        .findByKey(key.key())
        .map(Setting::getValue)
        .map(Boolean::parseBoolean)
        .orElseGet(() -> Boolean.parseBoolean(key.defaultValue()));
  }

  /** Creates or updates a boolean setting value in the settings repository */
  public void setBoolean(SettingKeys key, boolean value) {
    Objects.requireNonNull(key, "key must not be null");
    Setting setting =
        settingRepository
            .findByKey(key.key())
            .orElseGet(() -> new Setting(key.key(), String.valueOf(value)));

    setting.setValue(String.valueOf(value));
    settingRepository.save(setting);
  }

  /** Retrieves an integer setting value from the settings repository */
  public int getInt(SettingKeys key) {
    Objects.requireNonNull(key, "key must not be null");
    int fallback = Integer.parseInt(key.defaultValue());
    return settingRepository
        .findByKey(key.key())
        .map(Setting::getValue)
        .map(
            value -> {
              try {
                return Integer.parseInt(value);
              } catch (NumberFormatException e) {
                log.warn(
                    "Invalid int value for setting {} = {}, fallback to {}",
                    key.key(),
                    value,
                    fallback);
                return fallback;
              }
            })
        .orElse(fallback);
  }
}
