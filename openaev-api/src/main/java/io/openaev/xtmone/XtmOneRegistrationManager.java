package io.openaev.xtmone;

import io.openaev.config.OpenAEVConfig;
import io.openaev.ee.Ee;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmone.XtmOneClient.IntentCatalogEntry;
import io.openaev.xtmone.XtmOneClient.RegistrationInput;
import io.openaev.xtmone.XtmOneClient.RegistrationResponse;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically registers this OpenAEV instance with XTM One (filigran-copilot).
 *
 * <p>The /register endpoint is an upsert so repeated calls are safe and serve as both initial
 * registration and periodic heartbeat.
 *
 * <p>Only runs when both {@code xtm.one.url} and {@code xtm.one.token} are configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XtmOneRegistrationManager {

  private static final long BOOT_DELAY_SECONDS = 30;

  private final XtmOneConfig xtmOneConfig;
  private final XtmOneClient xtmOneClient;
  private final OpenAEVConfig openAEVConfig;
  private final PlatformSettingsService platformSettingsService;
  private final Ee ee;

  private volatile List<IntentCatalogEntry> intentCatalog = Collections.emptyList();

  public List<IntentCatalogEntry> getIntentCatalog() {
    return intentCatalog;
  }

  @PostConstruct
  void init() {
    if (xtmOneConfig.isConfigured()) {
      // Fire once at boot with a delay to let the platform finish init
      ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
      executor.schedule(
          () -> {
            try {
              doRegister();
            } catch (Exception e) {
              log.warn(
                  "[XTM One] Boot registration failed, will retry on next tick: {}",
                  e.getMessage());
            }
          },
          BOOT_DELAY_SECONDS,
          TimeUnit.SECONDS);
      executor.shutdown();
    }
  }

  /** Periodic registration — runs every 5 minutes. */
  @Scheduled(fixedRate = 300_000, initialDelay = 330_000)
  public void scheduledRegister() {
    if (!xtmOneConfig.isConfigured()) {
      return;
    }
    try {
      doRegister();
    } catch (Exception e) {
      log.error("[XTM One] Registration manager error: {}", e.getMessage(), e);
    }
  }

  private void doRegister() {
    var settings = platformSettingsService.findSettings();

    String licensePem = null;
    try {
      licensePem = ee.getEncodedCertificate();
    } catch (IllegalStateException ignored) {
      // No EE license — register as CE
    }

    RegistrationInput input = new RegistrationInput();
    input.setPlatformIdentifier("openaev");
    input.setPlatformUrl(openAEVConfig.getBaseUrl() != null ? openAEVConfig.getBaseUrl() : "");
    input.setPlatformTitle(
        openAEVConfig.getName() != null ? openAEVConfig.getName() : "OpenAEV Platform");
    input.setPlatformVersion(
        openAEVConfig.getVersion() != null ? openAEVConfig.getVersion() : "unknown");
    input.setPlatformId(settings.getPlatformId() != null ? settings.getPlatformId() : "");
    input.setEnterpriseLicensePem(licensePem);
    input.setBusinessVertical("aev");
    input.setIntents(XtmOneIntents.OPENAEV_INTENTS);

    RegistrationResponse result = xtmOneClient.register(input);

    if (result != null) {
      if (result.getIntentCatalog() != null) {
        intentCatalog = result.getIntentCatalog();
        int agentCount =
            intentCatalog.stream().mapToInt(e -> e.getAgents() != null ? e.getAgents().size() : 0).sum();
        log.info(
            "[XTM One] Intent catalog updated: {} intents, {} agents",
            intentCatalog.size(),
            agentCount);
      }
      log.info(
          "[XTM One] Registration successful: status={}, ee_enabled={}",
          result.getStatus(),
          result.isEeEnabled());
    } else {
      log.warn("[XTM One] Registration failed, will retry on next tick");
    }
  }
}
