package io.openaev.service.settings;

import static io.openaev.database.model.TenantSettingKeys.DEFAULT_LANG;
import static io.openaev.database.model.TenantSettingKeys.DEFAULT_THEME;
import static io.openaev.database.model.TenantSettingKeys.PLATFORM_NAME;
import static io.openaev.database.model.TenantSettingKeys.TENANT_HOME_DASHBOARD;
import static io.openaev.database.model.TenantSettingKeys.TENANT_SCENARIO_DASHBOARD;
import static io.openaev.database.model.TenantSettingKeys.TENANT_SIMULATION_DASHBOARD;

import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.model.Theme;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.settings.form.TenantSettingsUpdateInput;
import io.openaev.rest.settings.form.ThemeInput;
import io.openaev.rest.settings.response.TenantSettingsOutput;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantSettingsService {

  public static final String THEME_TYPE_LIGHT = "light";
  public static final String THEME_TYPE_DARK = "dark";

  private final SettingRepository settingRepository;

  // -- READ --

  /** Return all tenant-scoped settings for the given tenant, with platform fallback. */
  @Transactional(readOnly = true)
  public TenantSettingsOutput findSettings(@NotBlank String tenantId) {
    Map<String, Setting> tenantSettings = loadTenantSettings(tenantId);
    return buildTenantSettings(tenantSettings);
  }

  /** Find a single setting by key for the given tenant. */
  public Optional<Setting> findSetting(@NotBlank String tenantId, @NotBlank final String key) {
    return this.settingRepository.findByKeyAndTenantId(key, tenantId);
  }

  /**
   * Resolves the effective value for a tenant setting key (tenant override → platform fallback →
   * default).
   */
  @Transactional(readOnly = true)
  public String resolveSettingValue(@NotBlank String tenantId, TenantSettingKeys key) {
    Map<String, Setting> tenantSettings = loadTenantSettings(tenantId);
    return resolveValue(tenantSettings, key);
  }

  /**
   * Resolves the home dashboard ID for the given tenant. Returns empty when no dashboard is
   * configured (neither at tenant nor at platform level).
   */
  @Transactional(readOnly = true)
  public Optional<String> findHomeDashboardId(@NotBlank String tenantId) {
    Map<String, Setting> tenantSettings = loadTenantSettings(tenantId);
    String value = resolveValue(tenantSettings, TENANT_HOME_DASHBOARD);
    return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
  }

  // endregion

  // region -- UPDATE --
  public TenantSettingsOutput updateSettings(
      @NotBlank String tenantId, TenantSettingsUpdateInput input) {
    Tenant tenant = new Tenant(tenantId);
    upsert(tenant, PLATFORM_NAME.key(), input.platformName());
    upsert(tenant, DEFAULT_THEME.key(), input.platformTheme());
    upsert(tenant, DEFAULT_LANG.key(), input.platformLang());
    upsert(tenant, TENANT_HOME_DASHBOARD.key(), input.platformHomeDashboard());
    upsert(tenant, TENANT_SCENARIO_DASHBOARD.key(), input.platformScenarioDashboard());
    upsert(tenant, TENANT_SIMULATION_DASHBOARD.key(), input.platformSimulationDashboard());
    return findSettings(tenantId);
  }

  /** Update theme settings for a tenant. */
  public void updateTheme(@NotBlank String tenantId, @NotBlank String themeType, ThemeInput input) {
    Tenant tenant = new Tenant(tenantId);
    upsertThemeKey(
        tenant, themeType, Theme.THEME_KEYS.BACKGROUND_COLOR, input.getBackgroundColor());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.PAPER_COLOR, input.getPaperColor());
    upsertThemeKey(
        tenant, themeType, Theme.THEME_KEYS.NAVIGATION_COLOR, input.getNavigationColor());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.PRIMARY_COLOR, input.getPrimaryColor());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.SECONDARY_COLOR, input.getSecondaryColor());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.ACCENT_COLOR, input.getAccentColor());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.LOGO_URL, input.getLogoUrl());
    upsertThemeKey(
        tenant, themeType, Theme.THEME_KEYS.LOGO_URL_COLLAPSED, input.getLogoUrlCollapsed());
    upsertThemeKey(tenant, themeType, Theme.THEME_KEYS.LOGO_LOGIN_URL, input.getLogoLoginUrl());
  }

  /** Clear a tenant setting value if it matches the given value. */
  public void clearSettingIfMatch(
      @NotBlank String tenantId, @NotBlank String key, @NotBlank String value) {
    settingRepository
        .findByKeyAndTenantId(key, tenantId)
        .filter(s -> value.equals(s.getValue()))
        .ifPresent(
            s -> {
              s.setValue("");
              settingRepository.save(s);
            });
  }

  // -- Private helpers --

  private Map<String, Setting> loadTenantSettings(@NotBlank String tenantId) {
    return settingRepository.findAllByTenantId(tenantId).stream()
        .collect(Collectors.toMap(Setting::getKey, Function.identity()));
  }

  private TenantSettingsOutput buildTenantSettings(Map<String, Setting> tenantSettings) {
    return new TenantSettingsOutput(
        resolveValue(tenantSettings, PLATFORM_NAME),
        resolveValue(tenantSettings, DEFAULT_THEME),
        resolveValue(tenantSettings, DEFAULT_LANG),
        resolveValue(tenantSettings, TENANT_HOME_DASHBOARD),
        resolveValue(tenantSettings, TENANT_SCENARIO_DASHBOARD),
        resolveValue(tenantSettings, TENANT_SIMULATION_DASHBOARD),
        createThemeFromSettings(tenantSettings, THEME_TYPE_DARK),
        createThemeFromSettings(tenantSettings, THEME_TYPE_LIGHT));
  }

  private String resolveValue(Map<String, Setting> tenantSettings, TenantSettingKeys key) {
    // 1. Tenant override
    Setting tenantSetting = tenantSettings.get(key.key());
    if (tenantSetting != null) {
      return tenantSetting.getValue();
    }
    // 2. Platform fallback
    if (key.hasPlatformFallback()) {
      Optional<Setting> platformSetting = settingRepository.findByKeyAndTenantIsNull(key.key());
      if (platformSetting.isPresent()) {
        return platformSetting.get().getValue();
      }
    }
    // 3. Default value
    return key.defaultValue();
  }

  private void upsert(Tenant tenant, String key, String value) {
    Setting setting =
        settingRepository
            .findByKeyAndTenantId(key, tenant.getId())
            .orElseGet(
                () -> {
                  Setting s = new Setting(key, value);
                  s.setTenant(tenant);
                  return s;
                });
    setting.setValue(value);
    settingRepository.save(setting);
  }

  private void upsertThemeKey(
      Tenant tenant, String themeType, Theme.THEME_KEYS themeKey, String value) {
    String key = themeType + "." + themeKey.key();
    if (StringUtils.hasText(value)) {
      upsert(tenant, key, value);
    } else {
      settingRepository
          .findByKeyAndTenantId(key, tenant.getId())
          .ifPresent(settingRepository::delete);
    }
  }

  private ThemeInput createThemeFromSettings(Map<String, Setting> settings, String themeType) {
    ThemeInput theme = new ThemeInput();
    theme.setBackgroundColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.BACKGROUND_COLOR.key()));
    theme.setPaperColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.PAPER_COLOR.key()));
    theme.setNavigationColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.NAVIGATION_COLOR.key()));
    theme.setPrimaryColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.PRIMARY_COLOR.key()));
    theme.setSecondaryColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.SECONDARY_COLOR.key()));
    theme.setAccentColor(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.ACCENT_COLOR.key()));
    theme.setLogoUrl(getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.LOGO_URL.key()));
    theme.setLogoUrlCollapsed(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.LOGO_URL_COLLAPSED.key()));
    theme.setLogoLoginUrl(
        getSettingValue(settings, themeType + "." + Theme.THEME_KEYS.LOGO_LOGIN_URL.key()));
    return theme;
  }

  private String getSettingValue(Map<String, Setting> settings, String key) {
    return Optional.ofNullable(settings.get(key)).map(Setting::getValue).orElse(null);
  }
}
