package io.openaev.xtmone;

import static io.openaev.rest.attack_pattern.service.AttackPatternService.TTP_EXTRACTOR_INTENT;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
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
  private final EnterpriseEditionService eeService;

  private static final List<Map<String, String>> DEFAULT_INTENTS =
      List.of(
          Map.of(
              "name",
              "global.assistant",
              "description",
              "General-purpose assistant for adversary emulation"),
          Map.of("name", "summarize", "description", "Summarize content or findings"),
          Map.of("name", "make.it.shorter", "description", "Shorten or condense content"),
          Map.of("name", "make.it.longer", "description", "Expand or elaborate content"),
          Map.of("name", "fix.spelling", "description", "Fix spelling and grammar"),
          Map.of("name", "change.tone", "description", "Change tone of content"),
          Map.of("name", "explain", "description", "Explain content in simple terms"),
          Map.of(
              "name",
              TTP_EXTRACTOR_INTENT,
              "description",
              "Extract MITRE ATT&CK TTPs from documents and text"),
          Map.of(
              "name",
              "detection.generate",
              "description",
              "Generate detection and remediation rules for security collectors"),
          Map.of(
              "name",
              "generate.message",
              "description",
              "Generate email messages for adversary emulation injects"),
          Map.of(
              "name",
              "generate.media",
              "description",
              "Generate media articles for adversary emulation scenarios"));

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
      String platformName =
          settings.getPlatformName() != null ? settings.getPlatformName() : "OpenAEV Platform";

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
