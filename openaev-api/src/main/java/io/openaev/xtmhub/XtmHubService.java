package io.openaev.xtmhub;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.UserService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.LicenseUtils;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final TenantSettingsService tenantSettingsService;
  private final XtmHubConfig xtmHubConfig;
  private final XtmHubClient xtmHubClient;
  private final XtmHubEmailService xtmHubEmailService;
  private final TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;
  private final TenantRepository tenantRepository;

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
    registration.setConnectivityEmailEligible(true);
    return tenantXtmHubRegistrationRepository.save(registration);
  }

  public void autoRegister(@NotBlank final String token) {
    PlatformSettings settings = platformSettingsService.findSettings();
    Long usersCount = userService.globalCount();
    String tenantId = TenantContext.getCurrentTenant();
    String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse(tenantId);
    if (!xtmHubClient.autoRegister(
        token,
        LicenseUtils.computeXtmHubContractLevel(settings.getPlatformLicense()),
        settings.getPlatformId(),
        settings.getPlatformName(),
        settings.getPlatformBaseUrl(),
        settings.getPlatformVersion(),
        tenantId,
        tenantName,
        usersCount)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to register the platform on XtmHub");
    }
    TenantXtmHubRegistration registration = findOrCreateRegistration();
    registration.setToken(token);
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setLastConnectivityCheck(LocalDateTime.now());
    registration.setConnectivityEmailEligible(true);
    tenantXtmHubRegistrationRepository.save(registration);
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
      tenantXtmHubRegistrationRepository.deleteByTenantId(TenantContext.getCurrentTenant());
      return null;
    }

    return updateRegistrationStatus(registration.get(), checkResult);
  }

  public void refreshConnectivityAllTenants() {
    PlatformSettings settings = platformSettingsService.findSettings();

    List<TenantXtmHubRegistration> registrations =
        new ArrayList<>(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted());

    if (registrations.isEmpty()) {
      return;
    }

    Map<String, TenantRegistrationDetails> tenants = new HashMap<>();
    for (TenantXtmHubRegistration registration : registrations) {
      String tenantId = registration.getTenant().getId();
      String tenantName = registration.getTenant().getName();
      tenants.put(
          tenantId,
          new TenantRegistrationDetails(
              registration.getToken(), tenantSettingsService.buildTenantUrl(tenantId), tenantName));
    }

    Map<String, XtmHubConnectivityStatus> statuses =
        xtmHubClient.refreshRegistrationStatusAllTenants(
            settings.getPlatformId(), settings.getPlatformVersion(), tenants);

    List<ConnectivityCheckResult> allCheckResults = new ArrayList<>();

    try {
      for (TenantXtmHubRegistration registration : registrations) {
        TenantContext.setCurrentTenant(registration.getTenant().getId());

        XtmHubConnectivityStatus status =
            statuses.getOrDefault(
                registration.getTenant().getId(), XtmHubConnectivityStatus.INACTIVE);

        if (status == XtmHubConnectivityStatus.NOT_FOUND) {
          log.warn(
              "Platform was not found on XTM Hub for tenant {}", registration.getTenant().getId());
          tenantXtmHubRegistrationRepository.deleteByTenantId(registration.getTenant().getId());
          continue;
        }

        ConnectivityCheckResult checkResult =
            new ConnectivityCheckResult(
                status, parseLastConnectivityCheck(registration), registration);

        allCheckResults.add(checkResult);
        updateRegistrationStatus(registration, checkResult);
        handleTenantConnectivityLossNotification(settings, checkResult);
      }
    } finally {
      TenantContext.clearCurrentTenant();
    }

    handleConnectivityLossNotification(settings, allCheckResults);
  }

  private TenantXtmHubRegistration findOrCreateRegistration() {
    return tenantXtmHubRegistrationRepository
        .findByTenantId(TenantContext.getCurrentTenant())
        .orElse(new TenantXtmHubRegistration());
  }

  private ConnectivityCheckResult checkConnectivityStatus(
      PlatformSettings settings, TenantXtmHubRegistration registration) {
    String url = tenantSettingsService.buildTenantUrl(TenantContext.getCurrentTenant());
    String tenantName = registration.getTenant().getName();

    XtmHubConnectivityStatus status =
        xtmHubClient.refreshRegistrationStatusSingleTenant(
            settings.getPlatformId(),
            settings.getPlatformVersion(),
            registration.getToken(),
            url,
            TenantContext.getCurrentTenant(),
            tenantName);

    LocalDateTime lastCheck = parseLastConnectivityCheck(registration);

    return new ConnectivityCheckResult(status, lastCheck, registration);
  }

  public Boolean contactUs(String message) {
    Optional<TenantXtmHubRegistration> registration =
        tenantXtmHubRegistrationRepository.findByTenantId(Tenant.DEFAULT_TENANT_UUID);
    if (registration.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Default tenant is not registered on XtmHub");
    }
    String token = registration.get().getToken();
    String platformId = platformSettingsService.findSettings().getPlatformId();
    return xtmHubClient.contactUs(message, token, platformId);
  }

  private LocalDateTime parseLastConnectivityCheck(TenantXtmHubRegistration registration) {
    LocalDateTime lastCheck = registration.getLastConnectivityCheck();
    return lastCheck != null ? lastCheck : LocalDateTime.now();
  }

  private void handleConnectivityLossNotification(
      PlatformSettings settings, List<ConnectivityCheckResult> checkResults) {
    if (checkResults.isEmpty()) {
      return;
    }

    boolean connectivityRestored =
        checkResults.stream().anyMatch(r -> r.status() == XtmHubConnectivityStatus.ACTIVE);

    if (connectivityRestored) {
      platformSettingsService.updateXTMHubEmailNotification(true);
      return;
    }

    if (shouldSendConnectivityLossEmail(settings, checkResults)) {
      platformSettingsService.updateXTMHubEmailNotification(false);
      xtmHubEmailService.sendLostConnectivityEmail();
    }
  }

  private void handleTenantConnectivityLossNotification(
      PlatformSettings settings, ConnectivityCheckResult checkResult) {
    TenantXtmHubRegistration registration = checkResult.registration();
    if (checkResult.status() == XtmHubConnectivityStatus.ACTIVE) {
      if (!registration.isConnectivityEmailEligible()) {
        registration.setConnectivityEmailEligible(true);
        tenantXtmHubRegistrationRepository.save(registration);
      }
      return;
    }

    if (registration.isConnectivityEmailEligible()
        && hasConnectivityBeenLostForTooLong(checkResult.lastCheck())
        && xtmHubConfig.getConnectivityEmailEnable()) {
      xtmHubEmailService.sendTenantLostConnectivityEmail(
          registration.getTenant().getId(),
          tenantSettingsService.buildTenantUrl(registration.getTenant().getId()));
      registration.setConnectivityEmailEligible(false);
      tenantXtmHubRegistrationRepository.save(registration);
    }
  }

  private boolean shouldSendConnectivityLossEmail(
      PlatformSettings settings, List<ConnectivityCheckResult> checkResults) {

    return isEmailNotificationEnabled(settings)
        && checkResults.stream()
            .allMatch(
                r ->
                    r.status() != XtmHubConnectivityStatus.ACTIVE
                        && hasConnectivityBeenLostForTooLong(r.lastCheck()));
  }

  private boolean hasConnectivityBeenLostForTooLong(LocalDateTime lastCheck) {
    return lastCheck.isBefore(LocalDateTime.now().minusHours(CONNECTIVITY_EMAIL_THRESHOLD_HOURS));
  }

  private boolean isEmailNotificationEnabled(PlatformSettings settings) {
    return Boolean.parseBoolean(settings.getXtmHubShouldSendConnectivityEmail())
        && xtmHubConfig.getConnectivityEmailEnable();
  }

  private TenantXtmHubRegistration updateRegistrationStatus(
      TenantXtmHubRegistration registration, ConnectivityCheckResult checkResult) {

    XtmHubRegistrationStatus newStatus =
        checkResult.status() == XtmHubConnectivityStatus.ACTIVE
            ? XtmHubRegistrationStatus.REGISTERED
            : XtmHubRegistrationStatus.LOST_CONNECTIVITY;

    LocalDateTime updatedLastCheck =
        checkResult.status() == XtmHubConnectivityStatus.ACTIVE
            ? LocalDateTime.now()
            : checkResult.lastCheck();

    registration.setRegistrationStatus(newStatus);
    registration.setLastConnectivityCheck(updatedLastCheck);

    return tenantXtmHubRegistrationRepository.save(registration);
  }

  /** Encapsulates the result of a connectivity check */
  private record ConnectivityCheckResult(
      XtmHubConnectivityStatus status,
      LocalDateTime lastCheck,
      TenantXtmHubRegistration registration) {}
}
