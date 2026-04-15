package io.openaev.service.settings;

import static io.openaev.database.model.TenantSettingKeys.TENANT_HOME_DASHBOARD;
import static io.openaev.database.model.TenantSettingKeys.TENANT_SCENARIO_DASHBOARD;
import static io.openaev.database.model.TenantSettingKeys.TENANT_SIMULATION_DASHBOARD;

import io.openaev.database.model.Setting;
import io.openaev.database.model.TenantSetting;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.TenantSettingRepository;
import io.openaev.rest.settings.form.TenantSettingsUpdateInput;
import io.openaev.rest.settings.response.TenantSettingsOutput;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantSettingsService {

  private final TenantSettingRepository tenantSettingRepository;
  private final SettingRepository settingRepository;

  // -- READ --

  @Transactional(readOnly = true)
  public TenantSettingsOutput findSettings() {
    Map<String, TenantSetting> tenantSettings = loadTenantSettings();
    return buildTenantSettings(tenantSettings);
  }

  // -- UPDATE --

  public TenantSettingsOutput updateSettings(TenantSettingsUpdateInput input) {
    upsert(TENANT_HOME_DASHBOARD.key(), input.platformHomeDashboard());
    upsert(TENANT_SCENARIO_DASHBOARD.key(), input.platformScenarioDashboard());
    upsert(TENANT_SIMULATION_DASHBOARD.key(), input.platformSimulationDashboard());

    Map<String, TenantSetting> tenantSettings = loadTenantSettings();
    return buildTenantSettings(tenantSettings);
  }

  /**
   * Resolves the home dashboard ID for the current tenant. Returns empty when no dashboard is
   * configured (neither at tenant nor at platform level).
   */
  @Transactional(readOnly = true)
  public Optional<String> findHomeDashboardId() {
    Map<String, TenantSetting> tenantSettings = loadTenantSettings();
    String value = resolveValue(tenantSettings, TENANT_HOME_DASHBOARD);
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
  }

  // -- private helpers --

  private Map<String, TenantSetting> loadTenantSettings() {
    return StreamSupport.stream(tenantSettingRepository.findAll().spliterator(), false)
        .collect(Collectors.toMap(TenantSetting::getKey, Function.identity()));
  }

  private TenantSettingsOutput buildTenantSettings(Map<String, TenantSetting> tenantSettings) {
    return new TenantSettingsOutput(
        resolveValue(tenantSettings, TENANT_HOME_DASHBOARD),
        resolveValue(tenantSettings, TENANT_SCENARIO_DASHBOARD),
        resolveValue(tenantSettings, TENANT_SIMULATION_DASHBOARD));
  }

  private String resolveValue(Map<String, TenantSetting> tenantSettings, TenantSettingKeys key) {
    // 1. Tenant override exists → use it
    TenantSetting tenantSetting = tenantSettings.get(key.key());
    if (tenantSetting != null) {
      return tenantSetting.getValue();
    }

    // 2. Platform fallback enabled → look in platform parameters table
    if (key.hasPlatformFallback()) {
      Optional<Setting> platformSetting = settingRepository.findByKey(key.key());
      if (platformSetting.isPresent()) {
        return platformSetting.get().getValue();
      }
    }

    // 3. Default value
    return key.defaultValue();
  }

  private void upsert(String key, String value) {
    TenantSetting setting =
        tenantSettingRepository.findByKey(key).orElseGet(() -> new TenantSetting(key, value));
    setting.setValue(value);
    tenantSettingRepository.save(setting);
  }
}
