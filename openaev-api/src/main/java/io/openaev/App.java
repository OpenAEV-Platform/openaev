package io.openaev;

import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE_CREATION;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.tools.FlywayMigrationValidator;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class App {

  private final SettingRepository settingRepository;
  private final OpenAEVConfig openAEVConfig;

  public static void main(String[] args) {
    FlywayMigrationValidator.validateFlywayMigrationNames();
    SpringApplication.run(App.class, args);
  }

  @PostConstruct
  public void init() {
    log.info("Startup init");
    // Get the platform instance id
    Optional<Setting> instanceId = this.settingRepository.findByKey(PLATFORM_INSTANCE.key());
    Setting instanceCreationDate =
        this.settingRepository
            .findByKey(PLATFORM_INSTANCE_CREATION.key())
            .orElse(new Setting(PLATFORM_INSTANCE_CREATION.key(), ""));

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

      // Then we save the id in database and update/set the creation date
      settingRepository.save(instanceIdSetting);
      instanceCreationDate.setValue(Timestamp.from(Instant.now()).toString());
      settingRepository.save(instanceCreationDate);
    } else {
      platformId = instanceId.get().getValue();
    }
    log.info("Startup of the platform - Platform Instance ID: {}", platformId);
  }
}
