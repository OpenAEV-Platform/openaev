package io.openaev.service.connectors;

import static io.openaev.database.model.SettingKeys.*;

import io.openaev.api.xtm_composer.dto.XtmComposerInstanceOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.database.model.*;
import io.openaev.helper.ConnectorInstanceHashHelper;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.PlatformSettingsService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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

  /**
   * Maximum length of the instance name: XTM Composer uses it as the Kubernetes Deployment and
   * container name and as the Swarm service name, all capped at 63 characters (RFC 1123 label).
   */
  static final int INSTANCE_NAME_MAX_LENGTH = 63;

  private static final int INSTANCE_NAME_ID_LENGTH = 8;

  /**
   * The name XTM Composer deploys the instance under: the catalog title, a dash and the value of
   * the instance id key. A name that fits in {@link #INSTANCE_NAME_MAX_LENGTH} ASCII characters is
   * kept as is, so running deployments keep their name. A longer one becomes the slug of the title,
   * cut to fit, a dash and the first 8 characters of the id, which keeps the instances of one
   * catalog connector distinct.
   */
  static String instanceName(String title, String idValue) {
    if (idValue == null || idValue.isBlank()) {
      return title;
    }
    String name = title + "-" + idValue;
    if (name.length() <= INSTANCE_NAME_MAX_LENGTH && name.chars().allMatch(c -> c < 128)) {
      return name;
    }
    String idPart = slug(idValue);
    if (idPart.length() > INSTANCE_NAME_ID_LENGTH) {
      idPart = idPart.substring(0, INSTANCE_NAME_ID_LENGTH);
    }
    String titlePart = slug(title);
    int titleMax = INSTANCE_NAME_MAX_LENGTH - idPart.length() - 1;
    if (titlePart.length() > titleMax) {
      titlePart = titlePart.substring(0, titleMax).replaceAll("-+$", "");
    }
    if (titlePart.isEmpty()) {
      return idPart;
    }
    return idPart.isEmpty() ? titlePart : titlePart + "-" + idPart;
  }

  private static String slug(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  public XtmComposerInstanceOutput toXtmComposerInstanceOutput(
      ConnectorInstancePersisted instance) {
    String title = instance.getCatalogConnector().getTitle();
    return XtmComposerInstanceOutput.builder()
        .id(instance.getId())
        .name(
            instance.getConfigurations().stream()
                .filter(
                    connectorInstanceConfiguration ->
                        (instance.getCatalogConnector().getContainerType().getIdKeyName())
                            .equals(connectorInstanceConfiguration.getKey()))
                .findFirst()
                .map(
                    connectorInstanceConfiguration ->
                        instanceName(title, connectorInstanceConfiguration.getValue().asText()))
                .orElse(title))
        .currentStatus(instance.getCurrentStatus())
        .requestedStatus(instance.getRequestedStatus())
        .image(
            String.format(
                "%s:%s",
                instance.getCatalogConnector().getContainerImage(),
                instance.getCatalogConnector().getContainerVersion()))
        .hash(ConnectorInstanceHashHelper.computeInstanceHash(instance))
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
   * Check if the last connectivity check is older than 2 hours
   *
   * @param lastConnectivityCheckValue Last connectivity check value as string
   * @return true if the last connectivity check is older than 2 hours, false otherwise
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
    Setting composerIdSetting = xtmComposerInformation.get(XTM_COMPOSER_ID.key());
    if (composerIdSetting == null || !xtmComposerId.equals(composerIdSetting.getValue())) {
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

    Setting composerId = xtmComposerInformation.get(XTM_COMPOSER_ID.key());
    if (composerId == null || composerId.getValue() == null) {
      throw new BadRequestException("XTM Composer is not configured in the platform settings");
    }
    Setting lastCheck = xtmComposerInformation.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key());
    if (lastCheck == null
        || lastCheck.getValue() == null
        || isLastConnectivityCheckTooOld(lastCheck.getValue())) {
      throw new BadRequestException("XTM Composer is not reachable");
    }
  }

  /**
   * Non-throwing reachability probe. Mirrors {@link #throwIfXtmComposerNotReachable()} but returns
   * a boolean instead of throwing. Callers that only need a yes/no answer MUST use this: throwing a
   * {@link BadRequestException} from inside this {@code @Transactional} service marks the shared
   * transaction rollback-only, so a caller that catches the exception still fails at commit with an
   * {@code UnexpectedRollbackException}.
   *
   * @return true if XTM Composer is configured and its last connectivity check is recent enough
   */
  public boolean isXtmComposerReachable() {
    Map<String, Setting> xtmComposerInformation = this.getXtmComposerSettings();

    Setting composerId = xtmComposerInformation.get(XTM_COMPOSER_ID.key());
    if (composerId == null || composerId.getValue() == null) {
      return false;
    }
    Setting lastCheck = xtmComposerInformation.get(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key());
    return lastCheck != null
        && lastCheck.getValue() != null
        && !isLastConnectivityCheckTooOld(lastCheck.getValue());
  }
}
