package io.openaev;

import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE;
import static io.openaev.database.model.SettingKeys.PLATFORM_INSTANCE_CREATION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

class AppTest extends IntegrationTest {

  @Mock private SettingRepository settingRepository;
  @Autowired private OpenAEVConfig openAEVConfig;

  @DisplayName("Should throw an exception when having an incorrect instance id")
  @Test
  void shouldThrowExceptionWithInvalidInstanceId() {
    openAEVConfig.setInstanceId("pouet");
    when(settingRepository.findByKey(PLATFORM_INSTANCE.key())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new App(settingRepository, openAEVConfig).init())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid UUID string: pouet");
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
  }

  @DisplayName("Should update the instance id with the one in the properties")
  @Test
  void shouldUpdateInstanceIdWithProperty() {
    String uuid = UUID.randomUUID().toString();
    openAEVConfig.setInstanceId(uuid);
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
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
    when(settingRepository.findByKey(PLATFORM_INSTANCE.key())).thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
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
    when(settingRepository.findByKey(PLATFORM_INSTANCE.key())).thenReturn(Optional.empty());
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE.key().equals(setting.getKey())));
    verify(settingRepository)
        .save(argThat(setting -> PLATFORM_INSTANCE_CREATION.key().equals(setting.getKey())));
  }

  @DisplayName("Shouldn't update anything if instance id in db and the specified one is blank")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndSpecifiedOneIsBlank() {
    openAEVConfig.setInstanceId("");
    when(settingRepository.findByKey(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }

  @DisplayName("Shouldn't update anything if instance id in db and there is no specified one")
  @Test
  void shouldNotUpdateInstanceIdWhenInDbAndNoSpecifiedOne() {
    openAEVConfig.setInstanceId(null);
    when(settingRepository.findByKey(PLATFORM_INSTANCE.key()))
        .thenReturn(
            Optional.of(new Setting(PLATFORM_INSTANCE.key(), UUID.randomUUID().toString())));
    new App(settingRepository, openAEVConfig).init();
    verify(settingRepository).findByKey(PLATFORM_INSTANCE.key());
    verify(settingRepository).findByKey(PLATFORM_INSTANCE_CREATION.key());
    verifyNoMoreInteractions(settingRepository);
  }
}
