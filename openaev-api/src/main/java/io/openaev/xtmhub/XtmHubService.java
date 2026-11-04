package io.openaev.xtmhub;

import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NewsFeedItem;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.NewsFeedItemService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.LicenseUtils;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
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
  private final TenantWriteScopeResolver tenantWriteScopeResolver;
  private final TenantScopedTransaction tenantTx;
  private final PreviewFeatureService previewFeatureService;
  private final NewsFeedItemService newsFeedItemService;
  // Logged once per state change rather than at every run, as long as the tokens stay rejected.
  private final AtomicBoolean newsFeedTokensRejectedLogged = new AtomicBoolean(false);

  public Optional<TenantXtmHubRegistration> getRegistration(TxCtx ctx) {
    return tenantXtmHubRegistrationRepository.findByTenantId(
        singleTenantScope(ctx, "Reading the XTM Hub registration"));
  }

  /**
   * Searches the news feed items of the current user. The items carry no tenant, so the page is
   * empty when no tenant in the scope of the request is registered on XTM Hub (a tenant that lost
   * its connectivity still shows the items it retrieved).
   */
  public Page<NewsFeedItem> searchMyNewsFeedItems(
      TxCtx ctx, @NotNull final SearchPaginationInput searchPaginationInput) {
    if (!isAnyTenantRegistered(ctx)) {
      return new PageImpl<>(
          List.of(),
          PageRequest.of(searchPaginationInput.getPage(), searchPaginationInput.getSize()),
          0);
    }
    return newsFeedItemService.searchMyItems(searchPaginationInput);
  }

  private boolean isAnyTenantRegistered(TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return !tenantIds.isEmpty()
        && tenantXtmHubRegistrationRepository.existsByTenantIdInAndRegistrationStatusIn(
            tenantIds,
            EnumSet.of(
                XtmHubRegistrationStatus.REGISTERED, XtmHubRegistrationStatus.LOST_CONNECTIVITY));
  }

  public TenantXtmHubRegistration register(TxCtx ctx, @NotBlank final String token) {
    String tenantId = tenantWriteScopeResolver.tenantForWrite(ctx, null);
    User currentUser = userService.currentUser();

    TenantXtmHubRegistration registration = findOrCreateRegistration(tenantId);
    registration.setToken(token);
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setRegistrationUserId(currentUser.getId());
    registration.setRegistrationUserName(currentUser.getNameOrEmail());
    registration.setLastConnectivityCheck(LocalDateTime.now());
    registration.setConnectivityEmailEligible(true);
    return tenantXtmHubRegistrationRepository.save(registration);
  }

  public void autoRegister(TxCtx ctx, @NotBlank final String token) {
    PlatformSettings settings = platformSettingsService.findSettings();
    Long usersCount = userService.globalCount();
    String tenantId = tenantWriteScopeResolver.tenantForWrite(ctx, null);
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
    TenantXtmHubRegistration registration = findOrCreateRegistration(tenantId);
    registration.setToken(token);
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setLastConnectivityCheck(LocalDateTime.now());
    registration.setConnectivityEmailEligible(true);
    tenantXtmHubRegistrationRepository.save(registration);
  }

  public void unregister(TxCtx ctx) {
    tenantXtmHubRegistrationRepository.deleteByTenantId(
        tenantWriteScopeResolver.tenantForWrite(ctx, null));
  }

  public TenantXtmHubRegistration refreshConnectivity(TxCtx ctx) {
    String tenantId = singleTenantScope(ctx, "Refreshing XTM Hub connectivity");
    Optional<TenantXtmHubRegistration> registration =
        tenantXtmHubRegistrationRepository.findByTenantId(tenantId);

    if (registration.isEmpty()) {
      return null;
    }

    PlatformSettings settings = platformSettingsService.findSettings();
    ConnectivityCheckResult checkResult =
        checkConnectivityStatus(settings, registration.get(), tenantId);
    if (checkResult.status() == XtmHubConnectivityStatus.NOT_FOUND) {
      log.warn("Platform was not found on XTM Hub");
      tenantXtmHubRegistrationRepository.deleteByTenantId(tenantId);
      return null;
    }

    return updateRegistrationStatus(registration.get(), checkResult);
  }

  public void refreshConnectivityAllTenants() {
    PlatformSettings settings = platformSettingsService.findSettings();

    List<TenantXtmHubRegistration> registrations =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> new ArrayList<>(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()));

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
            settings.getPlatformId(),
            settings.getPlatformVersion(),
            LicenseUtils.computeXtmHubEdition(settings.getPlatformLicense()),
            tenants);

    List<ConnectivityCheckResult> allCheckResults = new ArrayList<>();

    tenantTx.forEachTenant(
        tenantId -> {
          if (!tenants.containsKey(tenantId)) {
            return;
          }
          TenantXtmHubRegistration registration =
              tenantXtmHubRegistrationRepository.findByTenantId(tenantId).orElse(null);
          if (registration == null) {
            return;
          }

          XtmHubConnectivityStatus status =
              statuses.getOrDefault(tenantId, XtmHubConnectivityStatus.INACTIVE);

          if (status == XtmHubConnectivityStatus.NOT_FOUND) {
            log.warn("Platform was not found on XTM Hub for tenant {}", tenantId);
            tenantXtmHubRegistrationRepository.deleteByTenantId(tenantId);
            return;
          }

          ConnectivityCheckResult checkResult =
              new ConnectivityCheckResult(
                  status, parseLastConnectivityCheck(registration), registration);
          updateRegistrationStatus(registration, checkResult);
          handleTenantConnectivityLossNotification(checkResult);
          allCheckResults.add(checkResult);
        });

    handleConnectivityLossNotification(settings, allCheckResults);
  }

  /**
   * Synchronizes the news feed of the platform with XTM Hub: consumes the items the Hub provisioned
   * for the platform, which is one call per run whatever the number of tenants, and creates them
   * for the users of the registered tenants. Registered tenants are tried in registration order
   * until one token is accepted.
   *
   * <p>Nothing is thrown: the Hub removes the items it returns, so those of a failed run are not
   * delivered again, and the run must not stop the scheduled task.
   */
  public void syncNewsFeed() {
    try {
      consumeAndCreateNewsFeedItems();
    } catch (Exception e) {
      log.error("The XTM Hub news feed items could not be created: {}", e.getMessage(), e);
    }
  }

  private void consumeAndCreateNewsFeedItems() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.XTM_HUB_NEWS_FEED)) {
      return;
    }

    List<TenantXtmHubRegistration> registrations =
        tenantTx
            .execute(
                TxCtx.allTenants(),
                () ->
                    new ArrayList<>(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()))
            .stream()
            .filter(r -> r.getRegistrationStatus() == XtmHubRegistrationStatus.REGISTERED)
            .sorted(
                Comparator.comparing(
                        TenantXtmHubRegistration::getRegistrationDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(r -> r.getTenant().getId()))
            .toList();
    if (registrations.isEmpty()) {
      log.info("No tenant is registered on XTM Hub, the news feed items are not consumed");
      return;
    }

    Set<String> registeredTenantIds =
        registrations.stream().map(r -> r.getTenant().getId()).collect(Collectors.toSet());
    String platformId = platformSettingsService.findSettings().getPlatformId();
    for (TenantXtmHubRegistration registration : registrations) {
      switch (xtmHubClient.consumeProvisionedNewsFeedItems(registration.getToken(), platformId)) {
        case NewsFeedFetchResult.Fetched fetched -> {
          newsFeedTokensRejectedLogged.set(false);
          newsFeedItemService.createMissingItems(fetched.payload().items(), registeredTenantIds);
          return;
        }
        case NewsFeedFetchResult.TokenRejected rejected ->
            log.warn(
                "XTM Hub rejected the registration token of tenant {}, trying the next tenant",
                registration.getTenant().getId());
        case NewsFeedFetchResult.Failed failed -> {
          return;
        }
      }
    }
    if (newsFeedTokensRejectedLogged.compareAndSet(false, true)) {
      log.error("XTM Hub rejected the registration token of every registered tenant");
    }
  }

  private TenantXtmHubRegistration findOrCreateRegistration(String tenantId) {
    return tenantXtmHubRegistrationRepository
        .findByTenantId(tenantId)
        .orElseGet(
            () -> {
              TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
              registration.setTenant(new Tenant(tenantId));
              return registration;
            });
  }

  private ConnectivityCheckResult checkConnectivityStatus(
      PlatformSettings settings, TenantXtmHubRegistration registration, String tenantId) {
    String url = tenantSettingsService.buildTenantUrl(tenantId);
    String tenantName = registration.getTenant().getName();

    XtmHubConnectivityStatus status =
        xtmHubClient.refreshRegistrationStatusSingleTenant(
            settings.getPlatformId(),
            settings.getPlatformVersion(),
            LicenseUtils.computeXtmHubEdition(settings.getPlatformLicense()),
            registration.getToken(),
            url,
            tenantId,
            tenantName);

    LocalDateTime lastCheck = parseLastConnectivityCheck(registration);

    return new ConnectivityCheckResult(status, lastCheck, registration);
  }

  public Boolean contactUs(String message) {
    // Deliberately reads the DEFAULT tenant's own registration, regardless of the caller's tenant
    // scope (this endpoint is skipRBAC and callable from any tenant). tenant_xtmhub_registrations
    // is v2-active, so the read must carry its own narrower scope. setScopeOnCurrentTransaction
    // joins the ambient request transaction already opened by the controller (rather than
    // executeNew's REQUIRES_NEW, which would open a second connection unable to see rows written
    // earlier, uncommitted, in this same transaction).
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
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

  private String singleTenantScope(TxCtx ctx, String operation) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    if (tenantIds.size() != 1) {
      throw new TenantWriteScopeException(
          operation + " requires a single-tenant scope. Provide an explicit" + " tenant selector.");
    }
    return tenantIds.iterator().next();
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

  private void handleTenantConnectivityLossNotification(ConnectivityCheckResult checkResult) {
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
