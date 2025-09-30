package io.openbas.service;

import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.database.model.SettingKeys.*;
import static io.openbas.helper.StreamHelper.fromIterable;
import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;

import io.openbas.config.EngineConfig;
import io.openbas.config.OpenBASConfig;
import io.openbas.config.OpenBASPrincipal;
import io.openbas.config.RabbitmqConfig;
import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.*;
import io.openbas.database.repository.SettingRepository;
import io.openbas.ee.Ee;
import io.openbas.ee.License;
import io.openbas.engine.EngineService;
import io.openbas.executors.caldera.config.CalderaExecutorConfig;
import io.openbas.expectation.ExpectationPropertiesConfig;
import io.openbas.helper.RabbitMQHelper;
import io.openbas.injectors.opencti.config.OpenCTIConfig;
import io.openbas.rest.exception.BadRequestException;
import io.openbas.rest.settings.PreviewFeature;
import io.openbas.rest.settings.form.*;
import io.openbas.rest.settings.response.OAuthProvider;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.rest.stream.ai.AiConfig;
import io.openbas.xtmhub.XtmHubConnectivityService;
import io.openbas.xtmhub.XtmHubRegistererRecord;
import io.openbas.xtmhub.XtmHubRegistrationStatus;
import io.openbas.xtmhub.config.XtmHubConfig;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformSettingsService {

  public static final String THEME_TYPE_LIGHT = "light";
  public static final String THEME_TYPE_DARK = "dark";

  @PersistenceContext private EntityManager entityManager;
  private final ApplicationContext context;
  private final Environment env;
  private final SettingRepository settingRepository;
  private final OpenCTIConfig openCTIConfig;
  private final XtmHubConfig xtmHubConfig;
  private final AiConfig aiConfig;
  private final CalderaExecutorConfig calderaExecutorConfig;
  private final Ee eeService;
  private final EngineService engineService;
  private final XtmHubConnectivityService xtmHubConnectivityService;

  @Autowired private TransactionTemplate transactionTemplate;

  @Value("${openbas.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${openbas.mail.imap.username}")
  private String imapUsername;

  @Resource private OpenBASConfig openBASConfig;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  @Resource private RabbitmqConfig rabbitmqConfig;
  @Resource private EngineConfig engineConfig;
  @Autowired private LicenseCacheManager licenseCacheManager;

  // -- PROVIDERS --
  private List<OAuthProvider> buildOpenIdProviders() {
    if (!this.openBASConfig.isAuthOpenidEnable()) {
      return new ArrayList<>();
    }
    try {
      OAuth2ClientProperties properties = this.context.getBean(OAuth2ClientProperties.class);
      Map<String, OAuth2ClientProperties.Registration> providers = properties.getRegistration();
      return providers.entrySet().stream()
          .map(
              entry -> {
                String uri = "/oauth2/authorization/" + entry.getKey();
                String clientName =
                    env.getProperty("openbas.provider." + entry.getKey() + ".login");
                // In case of missing name configuration, generate a generic name
                if (clientName == null) {
                  clientName = "Login with " + entry.getKey();
                }
                return new OAuthProvider(entry.getKey(), uri, clientName);
              })
          .toList();
    } catch (Exception e) {
      // No provider defined in the configuration
      return new ArrayList<>();
    }
  }

  private List<OAuthProvider> buildSaml2Providers() {
    if (!this.openBASConfig.isAuthSaml2Enable()) {
      return new ArrayList<>();
    }
    try {
      Saml2RelyingPartyProperties properties =
          this.context.getBean(Saml2RelyingPartyProperties.class);
      Map<String, Saml2RelyingPartyProperties.Registration> providers =
          properties.getRegistration();
      return providers.entrySet().stream()
          .map(
              entry -> {
                String uri = "/saml2/authenticate/" + entry.getKey();
                String clientName =
                    env.getProperty("openbas.provider." + entry.getKey() + ".login");
                // In case of missing name configuration, generate a generic name
                if (clientName == null) {
                  clientName = "Login with " + entry.getKey();
                }
                return new OAuthProvider(entry.getKey(), uri, clientName);
              })
          .toList();
    } catch (Exception e) {
      // No provider defined in the configuration
      return new ArrayList<>();
    }
  }

  // -- MAP UTILS --
  private Map<String, Setting> mapOfSettings(@NotBlank List<Setting> settings) {
    return settings.stream().collect(Collectors.toMap(Setting::getKey, Function.identity()));
  }

  private String getValueFromMapOfSettings(
      @NotBlank Map<String, Setting> dbSettings, @NotBlank final String key) {
    return Optional.ofNullable(dbSettings.get(key)).map(Setting::getValue).orElse(null);
  }

  private Setting resolveFromMap(Map<String, Setting> dbSettings, String themeKey, String value) {
    Optional<Setting> optionalSetting = ofNullable(dbSettings.get(themeKey));
    return resolve(optionalSetting, themeKey, value);
  }

  private Setting resolve(Optional<Setting> optionalSetting, String themeKey, String value) {
    if (optionalSetting.isPresent()) {
      Setting updateSetting = optionalSetting.get();
      updateSetting.setValue(value);
      return updateSetting;
    }
    return new Setting(themeKey, value);
  }

  // -- FIND SETTINGS --
  public PlatformSettings findSettings() {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    PlatformSettings platformSettings = new PlatformSettings();
    // Build anonymous settings
    platformSettings.setPlatformOpenIdProviders(buildOpenIdProviders());
    platformSettings.setPlatformSaml2Providers(buildSaml2Providers());
    platformSettings.setAuthOpenidEnable(openBASConfig.isAuthOpenidEnable());
    platformSettings.setAuthSaml2Enable(openBASConfig.isAuthSaml2Enable());
    platformSettings.setAuthLocalEnable(openBASConfig.isAuthLocalEnable());
    platformSettings.setPlatformTheme(
        ofNullable(dbSettings.get(DEFAULT_THEME.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_THEME.defaultValue()));
    platformSettings.setPlatformLang(
        ofNullable(dbSettings.get(DEFAULT_LANG.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_LANG.defaultValue()));
    platformSettings.setPlatformHomeDashboard(
        ofNullable(dbSettings.get(DEFAULT_HOME_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_HOME_DASHBOARD.defaultValue()));
    platformSettings.setPlatformScenarioDashboard(
        ofNullable(dbSettings.get(DEFAULT_SCENARIO_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_SCENARIO_DASHBOARD.defaultValue()));
    platformSettings.setPlatformSimulationDashboard(
        ofNullable(dbSettings.get(DEFAULT_SIMULATION_DASHBOARD.key()))
            .map(Setting::getValue)
            .orElse(DEFAULT_SIMULATION_DASHBOARD.defaultValue()));
    if (this.imapEnabled) {
      platformSettings.setDefaultMailer(this.imapUsername);
      platformSettings.setDefaultReplyTo(this.imapUsername);
    } else {
      platformSettings.setDefaultMailer(openBASConfig.getDefaultMailer());
      platformSettings.setDefaultReplyTo(openBASConfig.getDefaultReplyTo());
    }

    // Build authenticated user settings
    OpenBASPrincipal user = currentUser();
    if (user != null) {
      platformSettings.setPlatformWhitemark(
          ofNullable(dbSettings.get(PLATFORM_WHITEMARK.key()))
              .map(Setting::getValue)
              .orElse(PLATFORM_WHITEMARK.defaultValue()));
      platformSettings.setMapTileServerLight(openBASConfig.getMapTileServerLight());
      platformSettings.setMapTileServerDark(openBASConfig.getMapTileServerDark());
      platformSettings.setPlatformId(
          ofNullable(dbSettings.get(PLATFORM_INSTANCE.key()))
              .map(Setting::getValue)
              .orElse(PLATFORM_INSTANCE.defaultValue()));
      platformSettings.setPlatformName(
          ofNullable(dbSettings.get(PLATFORM_NAME.key()))
              .map(Setting::getValue)
              .orElse(PLATFORM_NAME.defaultValue()));
      platformSettings.setPlatformBaseUrl(openBASConfig.getBaseUrl());
      platformSettings.setPlatformAgentUrl(openBASConfig.getBaseUrlForAgent());
      platformSettings.setXtmOpenctiEnable(openCTIConfig.getEnable());
      platformSettings.setXtmOpenctiUrl(openCTIConfig.getUrl());
      platformSettings.setAiEnabled(aiConfig.isEnabled());
      platformSettings.setAiHasToken(StringUtils.hasText(aiConfig.getToken()));
      platformSettings.setAiType(aiConfig.getType());
      platformSettings.setAiModel(aiConfig.getModel());
      platformSettings.setExecutorCalderaEnable(calderaExecutorConfig.isEnable());
      platformSettings.setExecutorCalderaPublicUrl(calderaExecutorConfig.getPublicUrl());
      platformSettings.setExecutorTaniumEnable(false);
      platformSettings.setTelemetryManagerEnable(true);

      // Build admin settings
      if (user.isAdmin()) {
        platformSettings.setPlatformVersion(openBASConfig.getVersion());
        platformSettings.setPostgreVersion(settingRepository.getServerVersion());
        platformSettings.setJavaVersion(Runtime.version().toString());
        platformSettings.setRabbitMQVersion(RabbitMQHelper.getRabbitMQVersion(rabbitmqConfig));
        platformSettings.setAnalyticsEngineType(engineConfig.getEngineSelector());
        platformSettings.setAnalyticsEngineVersion(engineService.getEngineVersion());
      }
    }

    // THEME
    ThemeInput themeLight = createThemeInput(dbSettings, THEME_TYPE_LIGHT);
    platformSettings.setThemeLight(themeLight);

    ThemeInput themeDark = createThemeInput(dbSettings, THEME_TYPE_DARK);
    platformSettings.setThemeDark(themeDark);

    // POLICIES
    PolicyInput policies = new PolicyInput();
    policies.setLoginMessage(getValueFromMapOfSettings(dbSettings, PLATFORM_LOGIN_MESSAGE.key()));
    policies.setConsentMessage(
        getValueFromMapOfSettings(dbSettings, PLATFORM_CONSENT_MESSAGE.key()));
    policies.setConsentConfirmText(
        getValueFromMapOfSettings(dbSettings, PLATFORM_CONSENT_CONFIRM_TEXT.key()));
    platformSettings.setPolicies(policies);

    // FEATURE FLAG
    if (!StringUtils.hasText(openBASConfig.getEnabledDevFeatures())) {
      platformSettings.setEnabledDevFeatures(new ArrayList<>());
    } else {
      platformSettings.setEnabledDevFeatures(
          Arrays.stream(openBASConfig.getEnabledDevFeatures().split(","))
              .map(
                  featureStr -> {
                    try {
                      return PreviewFeature.fromStringIgnoreCase(featureStr.strip());
                    } catch (IllegalArgumentException e) {
                      log.warn(String.format("Unrecognised feature flag: %s", e.getMessage()), e);
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .distinct()
              .toList());
    }

    // PLATFORM MESSAGE
    Map<String, List<String>> platformBannerByLevel = new HashMap<>();
    for (BannerMessage.BANNER_KEYS bannerKey : BannerMessage.BANNER_KEYS.values()) {
      String value = getValueFromMapOfSettings(dbSettings, PLATFORM_BANNER + "." + bannerKey.key());
      if (value != null) {
        if (platformBannerByLevel.get(bannerKey.level().name()) == null) {
          platformBannerByLevel.put(
              bannerKey.level().name(), new ArrayList<>(Arrays.asList(bannerKey.message())));
        } else {
          platformBannerByLevel.get(bannerKey.level().name()).add(bannerKey.message());
        }
      }
    }
    platformSettings.setPlatformBannerByLevel(platformBannerByLevel);

    // EXPECTATION
    platformSettings.setDetectionExpirationTime(
        expectationPropertiesConfig.getDetectionExpirationTime());
    platformSettings.setPreventionExpirationTime(
        expectationPropertiesConfig.getPreventionExpirationTime());
    platformSettings.setVulnerabilityExpirationTime(
        expectationPropertiesConfig.getVulnerabilityExpirationTime());
    platformSettings.setChallengeExpirationTime(
        expectationPropertiesConfig.getChallengeExpirationTime());
    platformSettings.setArticleExpirationTime(
        expectationPropertiesConfig.getArticleExpirationTime());
    platformSettings.setManualExpirationTime(expectationPropertiesConfig.getManualExpirationTime());
    platformSettings.setExpectationDefaultScoreValue(
        expectationPropertiesConfig.getDefaultExpectationScoreValue());

    // License
    platformSettings.setPlatformLicense(licenseCacheManager.getEnterpriseEditionInfo());

    // XTM Hub
    platformSettings.setXtmHubEnable(xtmHubConfig.getEnable());
    platformSettings.setXtmHubUrl(xtmHubConfig.getUrl());
    platformSettings.setXtmHubReachable(xtmHubConnectivityService.isReachable());
    platformSettings.setXtmHubToken(getValueFromMapOfSettings(dbSettings, XTM_HUB_TOKEN.key()));
    platformSettings.setXtmHubRegistrationStatus(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_REGISTRATION_STATUS.key()));
    platformSettings.setXtmHubRegistrationDate(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_REGISTRATION_DATE.key()));
    platformSettings.setXtmHubRegistrationUserId(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_REGISTRATION_USER_ID.key()));
    platformSettings.setXtmHubRegistrationUserName(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_REGISTRATION_USER_NAME.key()));
    platformSettings.setXtmHubLastConnectivityCheck(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_LAST_CONNECTIVITY_CHECK.key()));
    platformSettings.setXtmHubShouldSendConnectivityEmail(
        getValueFromMapOfSettings(dbSettings, XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL.key()));
    return platformSettings;
  }

  private ThemeInput createThemeInput(Map<String, Setting> dbSettings, String themeType) {
    ThemeInput themeInput = new ThemeInput();
    themeInput.setBackgroundColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.BACKGROUND_COLOR.key()));
    themeInput.setPaperColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.PAPER_COLOR.key()));
    themeInput.setNavigationColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.NAVIGATION_COLOR.key()));
    themeInput.setPrimaryColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.PRIMARY_COLOR.key()));
    themeInput.setSecondaryColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.SECONDARY_COLOR.key()));
    themeInput.setAccentColor(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.ACCENT_COLOR.key()));
    themeInput.setLogoUrl(
        getValueFromMapOfSettings(dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL.key()));
    themeInput.setLogoLoginUrl(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_LOGIN_URL.key()));
    themeInput.setLogoUrlCollapsed(
        getValueFromMapOfSettings(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL_COLLAPSED.key()));
    return themeInput;
  }

  // -- UPDATE SETTINGS --
  public Optional<Setting> setting(String key) {
    return this.settingRepository.findByKey(key);
  }

  private void addSettingIfExists(
      List<Setting> settingsToSave, Map<String, Setting> dbSettings, String key, String value) {
    if (value != null) {
      settingsToSave.add(resolveFromMap(dbSettings, key, value));
    }
  }

  public PlatformSettings updateBasicConfigurationSettings(SettingsUpdateInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(resolveFromMap(dbSettings, PLATFORM_NAME.key(), input.getName()));
    settingsToSave.add(resolveFromMap(dbSettings, DEFAULT_THEME.key(), input.getTheme()));
    settingsToSave.add(resolveFromMap(dbSettings, DEFAULT_LANG.key(), input.getLang()));
    addSettingIfExists(
        settingsToSave, dbSettings, DEFAULT_HOME_DASHBOARD.key(), input.getHomeDashboard());
    addSettingIfExists(
        settingsToSave, dbSettings, DEFAULT_SCENARIO_DASHBOARD.key(), input.getScenarioDashboard());
    addSettingIfExists(
        settingsToSave,
        dbSettings,
        DEFAULT_SIMULATION_DASHBOARD.key(),
        input.getSimulationDashboard());
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateSettingsEnterpriseEdition(
      SettingsEnterpriseEditionUpdateInput input) throws Exception {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    String certPem = input.getEnterpriseEdition();
    if (certPem != null && !certPem.isEmpty()) {
      License license = eeService.verifyCertificate(certPem);
      if (!license.isLicenseValidated()) {
        throw new BadRequestException("Invalid certificate");
      }
    }
    settingsToSave.add(resolveFromMap(dbSettings, PLATFORM_ENTERPRISE_LICENSE.key(), certPem));
    settingRepository.saveAll(settingsToSave);
    licenseCacheManager.refreshLicense();
    return findSettings();
  }

  public PlatformSettings updateSettingsPlatformWhitemark(
      SettingsPlatformWhitemarkUpdateInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_WHITEMARK.key(), input.getPlatformWhitemark()));
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateSettingsPolicies(PolicyInput input) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_LOGIN_MESSAGE.key(), input.getLoginMessage()));
    settingsToSave.add(
        resolveFromMap(dbSettings, PLATFORM_CONSENT_MESSAGE.key(), input.getConsentMessage()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings, PLATFORM_CONSENT_CONFIRM_TEXT.key(), input.getConsentConfirmText()));
    settingRepository.saveAll(settingsToSave);
    return findSettings();
  }

  public PlatformSettings updateThemeLight(ThemeInput input) {
    return updateTheme(input, THEME_TYPE_LIGHT);
  }

  public PlatformSettings updateThemeDark(ThemeInput input) {
    return updateTheme(input, THEME_TYPE_DARK);
  }

  /**
   * Clear default platform dashboard settings if they match the provided dashboardId.
   *
   * @param dashboardId the dashboard ID to check against default settings
   */
  public void clearDefaultPlatformDashboardIfMatch(String dashboardId) {
    List<SettingKeys> clearableSettings =
        List.of(SettingKeys.DEFAULT_SCENARIO_DASHBOARD, SettingKeys.DEFAULT_SIMULATION_DASHBOARD);

    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));

    List<Setting> settingsToSave = new ArrayList<>();
    clearableSettings.forEach(
        setting -> {
          String currentValue = getValueFromMapOfSettings(dbSettings, setting.key());
          if (dashboardId.equals(currentValue)) {
            settingsToSave.add(resolveFromMap(dbSettings, setting.key(), setting.defaultValue()));
          }
        });
    settingRepository.saveAll(settingsToSave);
  }

  private PlatformSettings updateTheme(ThemeInput input, String themeType) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));
    List<Setting> settingsToSave = new ArrayList<>();

    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.BACKGROUND_COLOR.key(),
            input.getBackgroundColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.PAPER_COLOR.key(),
            input.getPaperColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.NAVIGATION_COLOR.key(),
            input.getNavigationColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.PRIMARY_COLOR.key(),
            input.getPrimaryColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.SECONDARY_COLOR.key(),
            input.getSecondaryColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.ACCENT_COLOR.key(),
            input.getAccentColor()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings, themeType + "." + Theme.THEME_KEYS.LOGO_URL.key(), input.getLogoUrl()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.LOGO_URL_COLLAPSED.key(),
            input.getLogoUrlCollapsed()));
    settingsToSave.add(
        resolveFromMap(
            dbSettings,
            themeType + "." + Theme.THEME_KEYS.LOGO_LOGIN_URL.key(),
            input.getLogoLoginUrl()));

    List<Setting> update = new ArrayList<>();
    List<String> delete = new ArrayList<>();
    settingsToSave.forEach(
        setting -> {
          if (StringUtils.hasText(setting.getValue())) {
            update.add(setting);
          } else if (StringUtils.hasText(setting.getId())) {
            delete.add(setting.getId());
          }
        });

    settingRepository.deleteAllById(delete);
    settingRepository.saveAll(update);
    return findSettings();
  }

  public PlatformSettings updateXTMHubRegistration(
      String token,
      LocalDateTime registrationDate,
      XtmHubRegistrationStatus registrationStatus,
      XtmHubRegistererRecord registerer,
      LocalDateTime lastConnectivityCheck,
      Boolean shouldSendConnectivityEmail) {
    Map<String, Setting> dbSettings = mapOfSettings(fromIterable(this.settingRepository.findAll()));

    Map<String, String> xtmhubSettingsMap = new HashMap<>();
    xtmhubSettingsMap.put(XTM_HUB_TOKEN.key(), token);
    xtmhubSettingsMap.put(
        XTM_HUB_REGISTRATION_DATE.key(),
        registrationDate != null ? registrationDate.toString() : null);
    xtmhubSettingsMap.put(XTM_HUB_REGISTRATION_STATUS.key(), registrationStatus.label);
    xtmhubSettingsMap.put(
        XTM_HUB_REGISTRATION_USER_ID.key(), registerer != null ? registerer.id() : null);
    xtmhubSettingsMap.put(
        XTM_HUB_REGISTRATION_USER_NAME.key(), registerer != null ? registerer.name() : null);
    xtmhubSettingsMap.put(
        XTM_HUB_LAST_CONNECTIVITY_CHECK.key(),
        lastConnectivityCheck != null ? lastConnectivityCheck.toString() : null);
    xtmhubSettingsMap.put(
        XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL.key(),
        shouldSendConnectivityEmail != null ? shouldSendConnectivityEmail.toString() : null);

    // Transaction 1: Perform modifications
    transactionTemplate.execute(
        status -> {
          List<Setting> allEntities = fromIterable(this.settingRepository.findAll());
          List<Setting> toDelete =
              allEntities.stream()
                  .filter(
                      s ->
                          xtmhubSettingsMap.containsKey(s.getKey())
                              && xtmhubSettingsMap.get(s.getKey()) == null)
                  .collect(Collectors.toList());
          List<Setting> toUpdate =
              allEntities.stream()
                  .filter(
                      s ->
                          xtmhubSettingsMap.containsKey(s.getKey())
                              && xtmhubSettingsMap.get(s.getKey()) != null)
                  .peek(s -> s.setValue(xtmhubSettingsMap.get(s.getKey())))
                  .collect(Collectors.toList());

          // Extract existing keys from toUpdate list
          Set<String> existingToUpdateKeys =
              toUpdate.stream().map(Setting::getKey).collect(Collectors.toSet());

          xtmhubSettingsMap.forEach(
              (key, value) -> {
                if (value != null && !existingToUpdateKeys.contains(key)) {
                  toUpdate.add(new Setting(key, value));
                }
              });
          this.settingRepository.deleteAll(toDelete);
          this.settingRepository.saveAll(toUpdate);
          return null;
        });

    // Transaction 2: Fetch fresh data
    return transactionTemplate.execute(status -> findSettings());
  }

  // -- PLATFORM MESSAGE --

  public void cleanMessage(@NotBlank final BannerMessage.BANNER_KEYS banner) {
    settingRepository.deleteByKeyIn(List.of(PLATFORM_BANNER + "." + banner.key()));
  }

  public void errorMessage(@NotBlank final BannerMessage.BANNER_KEYS banner) {
    Optional<Setting> bannerLevelOpt =
        this.settingRepository.findByKey(PLATFORM_BANNER + "." + banner.key());
    if (bannerLevelOpt.isEmpty()) {
      Setting bannerLevel =
          resolve(bannerLevelOpt, PLATFORM_BANNER + "." + banner.key(), banner.level().name());
      settingRepository.save(bannerLevel);
    }
  }

  public boolean isPlatformWhiteMarked() {
    String defaultValue = SettingKeys.PLATFORM_WHITEMARK.defaultValue();
    Optional<Setting> platformWhiteMarkedSetting =
        this.setting(SettingKeys.PLATFORM_WHITEMARK.name().toLowerCase());
    return platformWhiteMarkedSetting
        .map(setting -> parseBoolean(setting.getValue()))
        .orElse(parseBoolean(defaultValue));
  }
}
