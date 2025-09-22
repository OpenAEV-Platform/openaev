package io.openbas.xtmhub;

import io.openbas.database.model.User;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.service.PlatformSettingsService;
import io.openbas.service.UserService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class XtmHubService {
  private static final long CONNECTIVITY_EMAIL_THRESHOLD_HOURS = 24;

  private final PlatformSettingsService platformSettingsService;
  private final UserService userService;
  private final XtmHubClient xtmHubClient;
  private final XtmHubEmailService xtmHubEmailService;

  public PlatformSettings register(@NotBlank final String token) {
    User currentUser = userService.currentUser();
    return this.platformSettingsService.updateXTMHubRegistration(
        token,
        LocalDateTime.now(),
        XtmHubRegistrationStatus.REGISTERED,
        new XtmHubRegistererRecord(currentUser.getId(), currentUser.getName()),
        LocalDateTime.now(),
        true);
  }

  public PlatformSettings unregister() {
    return this.platformSettingsService.updateXTMHubRegistration(
        null, null, XtmHubRegistrationStatus.UNREGISTERED, null, null, null);
  }

  public PlatformSettings refreshConnectivity() {
    PlatformSettings settings = platformSettingsService.findSettings();

    if (!isRegisteredWithXtmHub(settings)) {
      return settings;
    }

    ConnectivityCheckResult checkResult = checkConnectivityStatus(settings);
    handleConnectivityLossNotification(settings, checkResult);

    return updateRegistrationStatus(settings, checkResult);
  }

  private boolean isRegisteredWithXtmHub(PlatformSettings settings) {
    return StringUtils.isNotBlank(settings.getXtmHubToken());
  }

  private ConnectivityCheckResult checkConnectivityStatus(PlatformSettings settings) {
    XtmHubConnectivityStatus status =
        xtmHubClient.refreshRegistrationStatus(
            settings.getPlatformId(), settings.getPlatformVersion(), settings.getXtmHubToken());

    boolean isActive = status == XtmHubConnectivityStatus.ACTIVE;
    LocalDateTime lastCheck = parseLastConnectivityCheck(settings);

    return new ConnectivityCheckResult(isActive, lastCheck);
  }

  private LocalDateTime parseLastConnectivityCheck(PlatformSettings settings) {
    String lastCheckStr = settings.getXtmHubLastConnectivityCheck();
    return lastCheckStr != null ? LocalDateTime.parse(lastCheckStr) : LocalDateTime.now();
  }

  private void handleConnectivityLossNotification(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {

    if (shouldSendConnectivityLossEmail(settings, checkResult)) {
      xtmHubEmailService.sendLostConnectivityEmail();
    }
  }

  private boolean shouldSendConnectivityLossEmail(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {

    return !checkResult.isActive()
        && hasConnectivityBeenLostForTooLong(checkResult.lastCheck())
        && isEmailNotificationEnabled(settings);
  }

  private boolean hasConnectivityBeenLostForTooLong(LocalDateTime lastCheck) {
    return lastCheck.isBefore(LocalDateTime.now().minusHours(CONNECTIVITY_EMAIL_THRESHOLD_HOURS));
  }

  private boolean isEmailNotificationEnabled(PlatformSettings settings) {
    return Boolean.parseBoolean(settings.getXtmHubShouldSendConnectivityEmail());
  }

  private PlatformSettings updateRegistrationStatus(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {

    XtmHubRegistrationStatus newStatus =
        checkResult.isActive()
            ? XtmHubRegistrationStatus.REGISTERED
            : XtmHubRegistrationStatus.LOST_CONNECTIVITY;

    LocalDateTime updatedLastCheck =
        checkResult.isActive() ? LocalDateTime.now() : checkResult.lastCheck();

    boolean shouldKeepEmailNotificationEnabled =
        !shouldSendConnectivityLossEmail(settings, checkResult);

    return platformSettingsService.updateXTMHubRegistration(
        settings.getXtmHubToken(),
        parseRegistrationDate(settings),
        newStatus,
        new XtmHubRegistererRecord(
            settings.getXtmHubRegistrationUserId(), settings.getXtmHubRegistrationUserName()),
        updatedLastCheck,
        shouldKeepEmailNotificationEnabled);
  }

  private LocalDateTime parseRegistrationDate(PlatformSettings settings) {
    return LocalDateTime.parse(settings.getXtmHubRegistrationDate());
  }

  /** Encapsulates the result of a connectivity check */
  private record ConnectivityCheckResult(boolean isActive, LocalDateTime lastCheck) {}
}
