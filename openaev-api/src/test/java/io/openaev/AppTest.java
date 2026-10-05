package io.openaev;

import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE_CREATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

class AppTest extends IntegrationTest {

  @Mock private SettingRepository settingRepository;
  @Autowired private OpenAEVConfig openAEVConfig;

  @DisplayName("Should throw an exception when having an incorrect instance id")
  @Test
  void shouldThrowExceptionWithInvalidInstanceId() {
    openAEVConfig.setInstanceId("pouet");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> new App(settingRepository, openAEVConfig).init())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid UUID string: pouet");
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
  }

  @DisplayName("Should update the instance id with the one in the properties")
  @Test
  void shouldUpdateInstanceIdWithProperty() {
    String uuid = UUID.randomUUID().toString();
    openAEVConfig.setInstanceId(uuid);
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(
            argThat(
                setting ->
                    PLATFORM_INSTANCE.key().equals(setting.getKey())
                        && uuid.equals(setting.getValue())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Should update the instance id when there is none in db")
  @Test
  void shouldUpdateInstanceIdWhenNoneInDb() {
    // Mock le repository pour retourner empty
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE.key().equals(setting.getKey())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName(
      "Should update the instance id when there is none in db and the specified one is blank")
  @Test
  void shouldUpdateInstanceIdWhenNoneInDbAndSpecifiedOneIsBlank() {
    openAEVConfig.setInstanceId("");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE.key().equals(setting.getKey())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Shouldn't update anything if instance id in db and the specified one is blank")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndSpecifiedOneIsBlank() {
    openAEVConfig.setInstanceId("");
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  @DisplayName("Shouldn't update anything if instance id in db and there is no specified one")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndNoSpecifiedOne() {
    openAEVConfig.setInstanceId(null);
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  private void givenStoredInstance(String instanceId, String creationDate) {
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key()))
        .thenReturn(Optional.of(new Setting(PLATFORM_INSTANCE.key(), instanceId)));
    when(settingRepository.findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key()))
        .thenReturn(
            creationDate == null
                ? Optional.empty()
                : Optional.of(new Setting(PLATFORM_INSTANCE_CREATION.key(), creationDate)));
  }

  @DisplayName("Should write the creation date of a new instance as an ISO-8601 instant")
  @Test
  void shouldWriteCreationDateAsInstantForNewInstance() {
    openAEVConfig.setInstanceId(null);
    Instant before = Instant.now();

    new App(settingRepository, openAEVConfig).init();

    ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
    verify(settingRepository, times(2)).save(saved.capture());
    Setting creation =
        saved.getAllValues().stream()
            .filter(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey()))
            .findFirst()
            .orElseThrow();
    assertThat(Instant.parse(creation.getValue())).isBetween(before, Instant.now());
  }

  @DisplayName("Should keep the creation date when restarted with another instance id")
  @Test
  void shouldKeepCreationDateWhenInstanceIdChanges() {
    String createdAt = "2026-09-01T08:00:00Z";
    givenStoredInstance(UUID.randomUUID().toString(), createdAt);

    // Two restarts, each with another configured instance id
    for (int restart = 0; restart < 2; restart++) {
      String configuredId = UUID.randomUUID().toString();
      openAEVConfig.setInstanceId(configuredId);
      new App(settingRepository, openAEVConfig).init();
      verify(settingRepository)
          .save(
              argThat(
                  setting ->
                      PLATFORM_INSTANCE.key().equals(setting.getKey())
                          && configuredId.equals(setting.getValue())));
    }

    verify(settingRepository, never())
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Should not set a missing creation date again on an existing instance")
  @Test
  void shouldNotSetMissingCreationDateOnExistingInstance() {
    openAEVConfig.setInstanceId(UUID.randomUUID().toString());
    givenStoredInstance(UUID.randomUUID().toString(), null);

    new App(settingRepository, openAEVConfig).init();

    verify(settingRepository, never())
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Should leave a creation date in the future unchanged")
  @Test
  void shouldLeaveFutureCreationDateUnchanged() {
    openAEVConfig.setInstanceId(null);
    String future = Instant.now().plus(365, ChronoUnit.DAYS).toString();
    givenStoredInstance(UUID.randomUUID().toString(), future);

    new App(settingRepository, openAEVConfig).init();

    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKeyAndTenantIsNull(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  @DisplayName("Should rewrite a legacy creation date as the same ISO-8601 instant")
  @Test
  void shouldRewriteLegacyCreationDateAsInstant() {
    openAEVConfig.setInstanceId(null);
    String legacy = "2026-09-01 10:00:00.123";
    givenStoredInstance(UUID.randomUUID().toString(), legacy);

    new App(settingRepository, openAEVConfig).init();

    Instant expected = Timestamp.valueOf(legacy).toInstant();
    verify(settingRepository)
        .save(
            argThat(
                setting ->
                    PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())
                        && setting.getValue().equals(expected.toString())));
  }
}
