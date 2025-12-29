package io.openaev.service.connectors;

import static io.openaev.database.model.SettingKeys.*;

import io.openaev.api.xtm_composer.dto.XtmComposerInstanceOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.Setting;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.PlatformSettingsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class XtmComposerService {

  private final PlatformSettingsService platformSettingsService;

  private XtmComposerOutput toXtmComposerOutput(String composerId, String composerVersion) {
    return XtmComposerOutput.builder().id(composerId).version(composerVersion).build();
  }

  private String hashWithSHA256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder(2 * hash.length);
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private String getImageVersionString(CatalogConnector catalogConnector) {
    if (catalogConnector.getContainerImage().isEmpty()) {
      return "";
    }
    return String.format(
        "%s:%s", catalogConnector.getContainerImage(), catalogConnector.getContainerVersion());
  }

  private String transformConnectorInstanceConfigurationToString(
      Set<ConnectorInstanceConfiguration> configurations) {
    return configurations.stream()
        .filter(c -> c != null && c.getKey() != null && c.getValue() != null)
        .sorted(Comparator.comparing(ConnectorInstanceConfiguration::getKey))
        .map(c -> String.format("%s=%s", c.getKey(), c.getValue()))
        .collect(Collectors.joining(";"));
  }

  private String computeInstanceHash(ConnectorInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("Instance cannot be null");
    }
    String image = getImageVersionString(instance.getCatalogConnector());
    String config = transformConnectorInstanceConfigurationToString(instance.getConfigurations());
    String dataToHash = String.format("IMAGE[%s]|CONFIG[%s]", image, config);
    return hashWithSHA256(dataToHash);
  }

  public XtmComposerInstanceOutput toXtmComposerInstanceOutput(ConnectorInstance instance) {
    return XtmComposerInstanceOutput.builder()
        .id(instance.getId())
        .name(instance.getCatalogConnector().getTitle())
        .currentStatus(instance.getCurrentStatus())
        .requestedStatus(instance.getRequestedStatus())
        .image(instance.getCatalogConnector().getContainerImage())
        .hash(computeInstanceHash(instance))
        .configurations(
            instance.getConfigurations().stream()
                .map(
                    c ->
                        XtmComposerInstanceOutput.Configuration.builder()
                            .key(c.getKey())
                            .value(c.getValue().asText())
                            .isEncrypted(c.isEncrypted())
                            .build())
                .toList())
        .build();
  }

  /**
   * Get XTM Composer settings from platform settings
   *
   * @return Map of XTM Composer settings
   */
  public Map<String, Setting> getXtmComposerSettings() {
    return platformSettingsService.findSettingsByKeys(
        List.of(
            XTM_COMPOSER_ID.key(),
            XTM_COMPOSER_NAME.key(),
            XTM_COMPOSER_VERSION.key(),
            XTM_COMPOSER_PUBLIC_KEY.key(),
            XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()));
  }

  /**
   * Register XTM Composer by saving its information into platform settings
   *
   * @param input XtmComposerRegisterInput
   * @return XtmComposerOutput
   */
  public XtmComposerOutput register(XtmComposerRegisterInput input) {
    Map<String, String> composerSettings = new HashMap<>();
    composerSettings.put(XTM_COMPOSER_ID.key(), input.getId());
    composerSettings.put(XTM_COMPOSER_NAME.key(), input.getName());
    composerSettings.put(
        XTM_COMPOSER_VERSION.key(), this.platformSettingsService.getPlatformVersion());
    composerSettings.put(XTM_COMPOSER_PUBLIC_KEY.key(), input.getPublicKey());
    composerSettings.put(
        XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), LocalDateTime.now().toString());

    Map<String, Setting> savedSettings = platformSettingsService.saveSettings(composerSettings);

    return toXtmComposerOutput(
        savedSettings.get(XTM_COMPOSER_ID.key()).getValue(),
        savedSettings.get(XTM_COMPOSER_VERSION.key()).getValue());
  }

  /**
   * Refresh connectivity with XTM Composer by updating last connectivity check time
   *
   * @param xtmComposerId XTM Composer id
   * @param lastConnectivityCheck Last connectivity check time
   * @return XtmComposerOutput
   */
  public XtmComposerOutput refreshConnectivity(
      String xtmComposerId, Instant lastConnectivityCheck) {
    Map<String, Setting> xtmComposerInformation = this.getXtmComposerSettings();
    if (!xtmComposerId.equals(xtmComposerInformation.get(XTM_COMPOSER_ID.key()).getValue())) {
      throw new BadRequestException("Invalid xtm-composer identifier");
    }

    platformSettingsService.saveSetting(
        XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), lastConnectivityCheck.toString());

    return toXtmComposerOutput(
        xtmComposerInformation.get(XTM_COMPOSER_ID.key()).getValue(),
        xtmComposerInformation.get(XTM_COMPOSER_VERSION.key()).getValue());
  }

  /**
   * Check if the last connectivity check is older than 1 day
   *
   * @param lastConnectivityCheckValue Last connectivity check value as string
   * @return true if the last connectivity check is older than 1 day, false otherwise
   */
  public boolean isLastConnectivityCheckTooOld(String lastConnectivityCheckValue) {
    try {
      Instant lastCheck = Instant.parse(lastConnectivityCheckValue);
      Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
      return lastCheck.isBefore(twoHoursAgo);
    } catch (Exception e) {
      log.error("Error parsing last connectivity check value: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Throw if XTM Composer identifier is not correct
   *
   * @param xtmComposerId XTM Composer id to validate
   * @throws BadRequestException if invalid xtm-composer ID
   */
  public void throwIfInvalidXtmComposerId(String xtmComposerId) throws BadRequestException {
    Map<String, Setting> xtmComposerInformation = this.getXtmComposerSettings();
    if (!xtmComposerId.equals(xtmComposerInformation.get(XTM_COMPOSER_ID.key()).getValue())) {
      throw new BadRequestException("Invalid xtm-composer identifier");
    }
  }

  /**
   * Throw if Xtm composer not reachable
   *
   * @throws BadRequestException if Xtm-Composer not reachable
   */
  public void throwIfXtmComposerNotReachable() throws BadRequestException {
    Map<String, Setting> xtmComposerInformation = this.getXtmComposerSettings();

    if (xtmComposerInformation.get(XTM_COMPOSER_ID.key()) == null
        || xtmComposerInformation.get(XTM_COMPOSER_ID.key()).getValue() == null) {
      throw new BadRequestException("XTM Composer is not configured in the platform settings");
    }
    if (xtmComposerInformation.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()).getValue() == null
        || isLastConnectivityCheckTooOld(
            xtmComposerInformation.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()).getValue())) {
      throw new BadRequestException("XTM Composer is not reachable");
    }
  }
}
