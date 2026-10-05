package io.openaev;

import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE_CREATION;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.debug.DebugLogCorrelationListener;
import io.openaev.debug.DebugTracingContextInitializer;
import io.openaev.tools.FlywayMigrationValidator;
import io.openaev.utils.InstanceCreationDate;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class App {

  private final SettingRepository settingRepository;
  private final OpenAEVConfig openAEVConfig;

  public static void main(String[] args) {
    FlywayMigrationValidator.validateFlywayMigrationNames();
    application().run(args);
  }

  /**
   * Builds the production application. {@link DebugTracingContextInitializer} excludes the tracing
   * auto-configuration unless debug mode is active; it is registered here for production and via
   * src/test/resources spring.factories for tests (an EnvironmentPostProcessor /
   * context.initializer.classes does not run under {@code @SpringBootTest}).
   */
  static SpringApplicationBuilder application() {
    return new SpringApplicationBuilder(App.class)
        .initializers(new DebugTracingContextInitializer())
        .listeners(new DebugLogCorrelationListener());
  }

  @PostConstruct
  public void init() {
    log.info("Startup init");
    // Get the platform instance id
    Optional<Setting> instanceId =
        this.settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    Optional<Setting> instanceCreationDate =
        this.settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());

    String platformId;

    // If we don't have a platform instance id or if it's been specified as another value than the
    // one in the database
    if (instanceId.isEmpty()
        || (!Strings.isBlank(openAEVConfig.getInstanceId())
            && !instanceId.get().getValue().equals(openAEVConfig.getInstanceId()))) {
      log.info("Updating platform instance id");
      // We update the platform instance id using a random UUID if the value does not exist in the
      // database
      platformId = UUID.randomUUID().toString();
      Setting instanceIdSetting =
          instanceId.orElse(new Setting(PLATFORM_INSTANCE.key(), platformId));

      // If it's been specified as a specific id, we validate that it's a proper UUID and use it
      if (!Strings.isBlank(openAEVConfig.getInstanceId())) {
        platformId = openAEVConfig.getInstanceId();
        instanceIdSetting.setValue(UUID.fromString(openAEVConfig.getInstanceId()).toString());
      }

      settingRepository.save(instanceIdSetting);
    } else {
      platformId = instanceId.get().getValue();
    }
    keepInstanceCreationDate(instanceCreationDate, instanceId.isEmpty());
    log.info("Startup of the platform - Platform Instance ID: {}", platformId);
  }

  /**
   * Keeps the earliest known creation date of this instance. It bounds a {@code ci} XTM license, so
   * it is written once, with the first instance id, and never moved afterwards: not when the
   * configured instance id changes, not when the value is missing, unreadable or in the future
   * (each of which refuses a {@code ci} license until the value is corrected). A value in the
   * legacy {@link java.sql.Timestamp} form is rewritten as the same instant in ISO-8601 UTC.
   */
  private void keepInstanceCreationDate(Optional<Setting> stored, boolean newInstance) {
    String value = stored.map(Setting::getValue).orElse(null);
    if (value == null || value.isBlank()) {
      if (newInstance) {
        Setting setting = stored.orElseGet(() -> new Setting(PLATFORM_INSTANCE_CREATION.key(), ""));
        setting.setValue(InstanceCreationDate.format(Instant.now()));
        settingRepository.save(setting);
      } else {
        log.error(
            "The creation date of this instance is missing and is not set again: a ci XTM license is"
                + " refused.");
      }
      return;
    }
    Optional<Instant> creationDate = InstanceCreationDate.parse(value);
    if (creationDate.isEmpty()) {
      log.error(
          "The creation date of this instance cannot be read (expected an ISO-8601 instant): a ci"
              + " XTM license is refused until it is corrected.");
      return;
    }
    if (creationDate.get().isAfter(Instant.now())) {
      log.error(
          "The creation date of this instance ({}) is in the future: a ci XTM license is refused"
              + " until it is corrected.",
          creationDate.get());
    }
    if (!InstanceCreationDate.isInstant(value)) {
      Setting setting = stored.get();
      setting.setValue(InstanceCreationDate.format(creationDate.get()));
      settingRepository.save(setting);
    }
  }
}
