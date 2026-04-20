package io.openaev.xtmhub;

import io.openaev.context.TenantContext;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.UserService;
import io.openaev.utils.LicenseUtils;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@AllArgsConstructor
public class XtmHubService {
  private static final long CONNECTIVITY_EMAIL_THRESHOLD_HOURS = 24;

  private final PlatformSettingsService platformSettingsService;
  private final UserService userService;
  private final XtmHubConfig xtmHubConfig;
  private final XtmHubClient xtmHubClient;
  private final XtmHubEmailService xtmHubEmailService;
  private final TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;

  public Optional<TenantXtmHubRegistration> getRegistration() {
    return tenantXtmHubRegistrationRepository.findByTenantId(TenantContext.getCurrentTenant());
  }

  public TenantXtmHubRegistration register(@NotBlank final String token) {
    User currentUser = userService.currentUser();

    TenantXtmHubRegistration registration = findOrCreateRegistration();
    registration.setToken(token);
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setRegistrationUserId(currentUser.getId());
    registration.setRegistrationUserName(currentUser.getName());
    registration.setLastConnectivityCheck(LocalDateTime.now());
    return tenantXtmHubRegistrationRepository.save(registration);
  }

  public void autoRegister(@NotBlank final String token) {
    PlatformSettings settings = platformSettingsService.findSettings();
    Long usersCount = userService.globalCount();
    if (!xtmHubClient.autoRegister(
        token,
        LicenseUtils.computeXtmHubContractLevel(settings.getPlatformLicense()),
        settings.getPlatformId(),
        settings.getPlatformName(),
        settings.getPlatformBaseUrl(),
        settings.getPlatformVersion(),
        usersCount)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to register the platform on XtmHub");
    }
    this.platformSettingsService.updateXTMHubRegistration(
        token, LocalDateTime.now(), XtmHubRegistrationStatus.REGISTERED, null, null, false);
  }

  public void unregister() {
    tenantXtmHubRegistrationRepository.deleteByTenantId(TenantContext.getCurrentTenant());
  }

  public TenantXtmHubRegistration refreshConnectivity() {
    Optional<TenantXtmHubRegistration> registration = getRegistration();

    if (registration.isEmpty()) {
      return null;
    }

    PlatformSettings settings = platformSettingsService.findSettings();
    ConnectivityCheckResult checkResult = checkConnectivityStatus(settings, registration.get());
    if (checkResult.status() == XtmHubConnectivityStatus.NOT_FOUND) {
      log.warn("Platform was not found on XTM Hub");
      platformSettingsService.deleteXTMHubRegistration();
      return null;
    }

    handleConnectivityLossNotification(settings, checkResult);

    return updateRegistrationStatus(settings, registration.get(), checkResult);
  }

  private TenantXtmHubRegistration findOrCreateRegistration() {
    return tenantXtmHubRegistrationRepository
        .findByTenantId(TenantContext.getCurrentTenant())
        .orElse(new TenantXtmHubRegistration());
  }

  private ConnectivityCheckResult checkConnectivityStatus(
      PlatformSettings settings, TenantXtmHubRegistration registration) {
    String url = settings.getPlatformBaseUrl() + "/" + TenantContext.getCurrentTenant();

    XtmHubConnectivityStatus status =
        xtmHubClient.refreshRegistrationStatusSingleTenant(
            settings.getPlatformId(),
            settings.getPlatformVersion(),
            registration.getToken(),
            url,
            TenantContext.getCurrentTenant());

    LocalDateTime lastCheck = parseLastConnectivityCheck(registration);

    return new ConnectivityCheckResult(status, lastCheck);
  }

  public Boolean contactUs(String message) {
    PlatformSettings settings = platformSettingsService.findSettings();
    String token = settings.getXtmHubToken();
    String platformId = settings.getPlatformId();
    return xtmHubClient.contactUs(message, token, platformId);
  }

  private LocalDateTime parseLastConnectivityCheck(TenantXtmHubRegistration registration) {
    LocalDateTime lastCheck = registration.getLastConnectivityCheck();
    return lastCheck != null ? lastCheck : LocalDateTime.now();
  }

  private void handleConnectivityLossNotification(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {

    if (shouldSendConnectivityLossEmail(settings, checkResult)) {
      xtmHubEmailService.sendLostConnectivityEmail();
    }
  }

  private boolean shouldSendConnectivityLossEmail(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {

    return checkResult.status() != XtmHubConnectivityStatus.ACTIVE
        && hasConnectivityBeenLostForTooLong(checkResult.lastCheck())
        && isEmailNotificationEnabled(settings);
  }

  private boolean hasConnectivityBeenLostForTooLong(LocalDateTime lastCheck) {
    return lastCheck.isBefore(LocalDateTime.now().minusHours(CONNECTIVITY_EMAIL_THRESHOLD_HOURS));
  }

  private boolean isEmailNotificationEnabled(PlatformSettings settings) {
    return Boolean.parseBoolean(settings.getXtmHubShouldSendConnectivityEmail())
        && xtmHubConfig.getConnectivityEmailEnable();
  }

  private TenantXtmHubRegistration updateRegistrationStatus(
      PlatformSettings settings,
      TenantXtmHubRegistration registration,
      ConnectivityCheckResult checkResult) {

    XtmHubRegistrationStatus newStatus =
        checkResult.status() == XtmHubConnectivityStatus.ACTIVE
            ? XtmHubRegistrationStatus.REGISTERED
            : XtmHubRegistrationStatus.LOST_CONNECTIVITY;

    LocalDateTime updatedLastCheck =
        checkResult.status() == XtmHubConnectivityStatus.ACTIVE
            ? LocalDateTime.now()
            : checkResult.lastCheck();

    boolean shouldKeepEmailNotificationEnabled =
        !shouldSendConnectivityLossEmail(settings, checkResult);

    platformSettingsService.updateXTMHubEmailNotification(shouldKeepEmailNotificationEnabled);
    registration.setRegistrationStatus(newStatus);
    registration.setLastConnectivityCheck(updatedLastCheck);

    return tenantXtmHubRegistrationRepository.save(registration);
  }

  /** Encapsulates the result of a connectivity check */
  private record ConnectivityCheckResult(
      XtmHubConnectivityStatus status, LocalDateTime lastCheck) {}
}
