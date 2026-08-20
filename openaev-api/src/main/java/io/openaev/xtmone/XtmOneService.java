package io.openaev.xtmone;

import static io.openaev.database.model.TenantSettingKeys.PLATFORM_NAME;

import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.settings.TenantSettingsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class XtmOneService {

  private final XtmOneConfig config;
  private final XtmOneClient client;
  private final PlatformSettingsService platformSettingsService;
  private final TenantSettingsService tenantSettingsService;
  private final EnterpriseEditionService eeService;

  private static final List<Map<String, String>> DEFAULT_INTENTS =
      List.of(
          Map.of("name", "global.assistant", "description", "General-purpose assistant"),
          Map.of("name", "global.summarize", "description", "Summarize content"),
          Map.of("name", "global.make_it_shorter", "description", "Shorten / summarize content"),
          Map.of("name", "global.make_it_longer", "description", "Expand / elaborate content"),
          Map.of("name", "global.fix_spelling", "description", "Fix spelling and grammar"),
          Map.of("name", "global.change_tone", "description", "Change the tone of content"),
          Map.of("name", "global.explain", "description", "Explain content in simple terms"),
          Map.of(
              "name",
              "cti.ttp_harvester",
              "description",
              "Extract TTPs (Tactics, Techniques and Procedures) from documents"),
          Map.of(
              "name",
              "aev.detection_rules_generator",
              "description",
              "Generate detection rules from malware behavior"),
          Map.of(
              "name",
              "aev.message_generator",
              "description",
              "Generate messages for adversary emulation exercises"),
          Map.of(
              "name",
              "aev.media_article_generator",
              "description",
              "Generate media articles for adversary emulation"),
          Map.of(
              "name",
              "aev.phishing_email_html_generator",
              "description",
              "Generate phishing email HTML templates for authorized awareness exercises"),
          Map.of(
              "name",
              "aev.phishing_landing_page_html_generator",
              "description",
              "Generate phishing landing page HTML/CSS for authorized awareness exercises"));

  /**
   * Register this platform with XTM One. Called on every connectivity tick (the /register endpoint
   * is an upsert, so repeated calls are safe). Sends the current license state, business vertical,
   * and declared intents for agent binding.
   */
  @Transactional(readOnly = true)
  public void autoRegister() {
    if (!config.isConfigured()) {
      return;
    }
    try {
      PlatformSettings settings = platformSettingsService.findSettings();
      String licensePem = null;
      try {
        licensePem = eeService.getEncodedCertificate();
      } catch (Exception ignored) {
        // CE platform or NFR — certificate not available as PEM
      }

      String licenseType = null;
      try {
        var license = eeService.getEnterpriseEditionInfo();
        if (license != null && license.isLicenseValidated()) {
          licenseType =
              license.getType() != null ? license.getType().name().toLowerCase() : "enterprise";
        }
      } catch (Exception ignored) {
        // license info not available
      }

      String version = platformSettingsService.getPlatformVersion();
      String platformUrl =
          settings.getPlatformBaseUrl() != null ? settings.getPlatformBaseUrl() : "";
      // The platform name is stored per-tenant (Settings → Parameters), but
      // PlatformSettings#getPlatformName only reads the platform-level (tenant-null) value, so a
      // rename would never reach XTM One. Resolve it the same way the UI does (tenant override →
      // platform fallback → default). This runs on a background scheduler thread with no request
      // scope, so resolve against the default tenant explicitly rather than relying on the
      // (possibly stale) TenantContext ThreadLocal.
      String platformName =
          tenantSettingsService.resolveSettingValue(Tenant.DEFAULT_TENANT_UUID, PLATFORM_NAME);
      if (platformName == null || platformName.isBlank()) {
        platformName = PLATFORM_NAME.defaultValue();
      }

      config.setPlatformUrl(platformUrl);
      config.setPlatformVersion(version != null ? version : "");

      Map<String, Object> result =
          client.register(
              "openaev",
              platformUrl,
              platformName,
              version != null ? version : "",
              settings.getPlatformId() != null ? settings.getPlatformId() : "",
              licensePem,
              licenseType,
              "aev",
              DEFAULT_INTENTS);
      if (result != null) {
        log.info(
            "[XTM One] Registration successful (ee_enabled={})",
            result.getOrDefault("ee_enabled", false));
      } else {
        log.warn("[XTM One] Registration failed, will retry on next tick");
      }
    } catch (Exception e) {
      log.warn("[XTM One] Registration failed, will retry on next tick", e);
    }
  }
}
