package io.openaev.service.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.exception.BadRequestException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

@ExtendWith(MockitoExtension.class)
@DisplayName("Runtime settings: env var > UI value > configured default")
class RuntimeSettingsServiceTest {

  @Mock private SettingRepository settingRepository;

  private StandardEnvironment environment;
  private RuntimeSettingsService service;

  @BeforeEach
  void setUp() {
    environment = new StandardEnvironment();
    // Replace the real process environment and system properties with controlled ones.
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .addLast(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new java.util.HashMap<>()));
    service = new RuntimeSettingsService(settingRepository, environment);
  }

  private void setEnv(String name, String value) {
    @SuppressWarnings("unchecked")
    Map<String, Object> source =
        (Map<String, Object>)
            environment
                .getPropertySources()
                .get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
                .getSource();
    source.put(name, value);
  }

  private void setProperty(String name, String value) {
    environment.getPropertySources().addFirst(new MapPropertySource("app", Map.of(name, value)));
  }

  private void store(RuntimeSetting setting, String value) {
    when(settingRepository.findByKeyAndTenantIsNull(setting.key()))
        .thenReturn(Optional.of(new Setting(setting.key(), value)));
  }

  @Test
  @DisplayName("given_nothingSet_should_useTheFallback")
  void given_nothingSet_should_useTheFallback() {
    when(settingRepository.findByKeyAndTenantIsNull(any())).thenReturn(Optional.empty());

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.AGENT_MAX_SIMULTANEOUS_JOBS);

    assertEquals("5", resolved.value());
    assertEquals(RuntimeSettingsService.Source.DEFAULT, resolved.source());
  }

  @Test
  @DisplayName("given_propertyOnly_should_useItAsTheDefault")
  void given_propertyOnly_should_useItAsTheDefault() {
    when(settingRepository.findByKeyAndTenantIsNull(any())).thenReturn(Optional.empty());
    setProperty("inject.execution.threshold.minutes", "25");

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES);

    assertEquals("25", resolved.value());
    assertEquals(RuntimeSettingsService.Source.DEFAULT, resolved.source());
  }

  @Test
  @DisplayName("given_uiValue_should_winOverTheProperty")
  void given_uiValue_should_winOverTheProperty() {
    setProperty("inject.execution.threshold.minutes", "25");
    store(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES, "60");

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES);

    assertEquals("60", resolved.value());
    assertEquals(RuntimeSettingsService.Source.UI, resolved.source());
  }

  @Test
  @DisplayName("given_envVar_should_winOverTheUiValueAndLock")
  void given_envVar_should_winOverTheUiValueAndLock() {
    setEnv("INJECT_EXECUTION_THRESHOLD_MINUTES", "90");

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES);

    assertEquals("90", resolved.value());
    assertEquals(RuntimeSettingsService.Source.ENV, resolved.source());
    assertTrue(resolved.locked());
    assertEquals("INJECT_EXECUTION_THRESHOLD_MINUTES", resolved.envVar());
  }

  @Test
  @DisplayName("given_dashedEnvVar_should_lockAndNameIt")
  void given_dashedEnvVar_should_lockAndNameIt() {
    setEnv("EXECUTOR_OPENAEV_AGENT_AUTO-UPDATE-ENABLED", "false");

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.AGENT_AUTO_UPDATE_ENABLED);

    assertEquals("false", resolved.value());
    assertTrue(resolved.locked());
    assertEquals("EXECUTOR_OPENAEV_AGENT_AUTO-UPDATE-ENABLED", resolved.envVar());
  }

  @Test
  @DisplayName("given_invalidEnvVar_should_beIgnored")
  void given_invalidEnvVar_should_beIgnored() {
    setEnv("OPENAEV_AGENT_QUEUE_THRESHOLD", "lots");
    when(settingRepository.findByKeyAndTenantIsNull(any())).thenReturn(Optional.empty());

    RuntimeSettingsService.Resolved resolved =
        service.resolve(RuntimeSetting.AGENT_QUEUE_THRESHOLD);

    assertEquals("0", resolved.value());
    assertEquals(RuntimeSettingsService.Source.DEFAULT, resolved.source());
  }

  @Test
  @DisplayName("given_envVarSet_should_refuseTheWrite")
  void given_envVarSet_should_refuseTheWrite() {
    setEnv("INJECT_EXECUTION_THRESHOLD_MINUTES", "90");

    BadRequestException error =
        assertThrows(
            BadRequestException.class,
            () ->
                service.update(
                    Map.of(RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES.key(), 30)));

    assertTrue(error.getMessage().contains("INJECT_EXECUTION_THRESHOLD_MINUTES"));
    verify(settingRepository, never()).save(any());
  }

  @Test
  @DisplayName("given_validValue_should_storeIt")
  void given_validValue_should_storeIt() {
    when(settingRepository.findByKeyAndTenantIsNull(any())).thenReturn(Optional.empty());

    service.update(Map.of(RuntimeSetting.AGENT_QUEUE_THRESHOLD.key(), 20));

    verify(settingRepository).save(any(Setting.class));
  }
}
