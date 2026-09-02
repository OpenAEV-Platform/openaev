package io.openaev.xtmhub;

import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class XtmHubStartupAutoRegisterService {

  private final XtmHubService xtmHubService;
  private final XtmHubConfig xtmHubConfig;
  private final XtmHubConnectivityService xtmHubConnectivityService;

  /**
   * Attempts to auto-register the platform in XTM Hub at startup when the required platform token
   * is available and the current tenant is not already registered.
   */
  @PostConstruct
  public void init() {
    String platformToken = xtmHubConfig.getPlatformToken();
    if (StringUtils.isBlank(platformToken)) {
      log.debug("[XTM Hub] Startup auto-registration skipped: platform token is missing");
      return;
    }

    if (!xtmHubConfig.getEnable()) {
      log.debug("[XTM Hub] Startup auto-registration skipped: XTM Hub is disabled");
      return;
    }

    if (!xtmHubConnectivityService.isReachable()) {
      log.info("[XTM Hub] Startup auto-registration skipped: XTM Hub is not reachable");
      return;
    }

    if (xtmHubService.getRegistration().isPresent()) {
      log.info("[XTM Hub] Startup auto-registration skipped: platform is already registered");
      return;
    }

    try {
      xtmHubService.autoRegister(platformToken);
      log.info("[XTM Hub] Startup auto-registration completed");
    } catch (Exception e) {
      // This flow must never fail the platform startup.
      log.warn(
          "[XTM Hub] Startup auto-registration failed and was ignored: {}",
          Objects.toString(e.getMessage(), "unknown error"),
          e);
    }
  }
}
