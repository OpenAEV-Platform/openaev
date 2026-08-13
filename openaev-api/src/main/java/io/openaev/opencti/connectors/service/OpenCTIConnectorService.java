package io.openaev.opencti.connectors.service;

import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.stix.objects.Bundle;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenCTIConnectorService {
  @Getter private List<ConnectorBase> connectors = Collections.emptyList();
  private final XtmConfig xtmConfig;
  private final OpenAEVConfig openAEVConfig;
  private final OpenCTIService openCTIService;

  /**
   * When a connector cannot register/ping (OpenCTI unreachable or its token not yet authorized),
   * the register-ping job runs every cycle and used to log a full ERROR stack each time. This is an
   * expected, self-healing transient condition, so we log one concise WARN per connector and then
   * stay silent for this backoff window until it recovers (a success resets the throttle).
   */
  private static final Duration REGISTER_FAILURE_WARN_BACKOFF = Duration.ofMinutes(30);

  private final Map<String, Instant> lastRegisterFailureWarnAt = new ConcurrentHashMap<>();

  /** Creates one {@link SecurityCoverageConnector} per tenant entry in the config map. */
  @PostConstruct
  public void initializeConnectors() {
    if (xtmConfig.getOpencti() == null || xtmConfig.getOpencti().isEmpty()) {
      this.connectors = Collections.emptyList();
      return;
    }

    List<ConnectorBase> configured = new ArrayList<>();
    xtmConfig
        .getOpencti()
        .forEach(
            (tenantId, config) -> {
              try {
                if (!config.isValid()) {
                  return;
                }
                SecurityCoverageConnector connector = new SecurityCoverageConnector();
                connector.setTenantId(tenantId);
                connector.setOpenCTIConfig(config);
                connector.setOpenAEVConfig(openAEVConfig);
                configured.add(connector);
              } catch (Exception e) {
                log.error(
                    "Failed to initialize OpenCTI connector for tenant {}. Skipping.", tenantId, e);
              }
            });
    this.connectors = List.copyOf(configured);
  }

  @NotNull
  public Optional<ConnectorBase> getConnectorBase(String tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId cannot be null");
    }
    return connectors.stream()
        .filter(
            c ->
                c instanceof SecurityCoverageConnector
                    && c.shouldRegister()
                    && Objects.equals(c.getTenantId(), tenantId))
        .findFirst();
  }

  public List<ConnectorBase> getRegisterConnectors() {
    return connectors.stream().filter(ConnectorBase::isRegistered).toList();
  }

  /**
   * Register or pings all loaded connectors. Does not crash if registering or pinging a connector
   * raises an exception, but logs a warning.
   */
  public void registerOrPingAllConnectors() {
    List<ConnectorBase> enabledConnectors =
        connectors.stream().filter(ConnectorBase::shouldRegister).toList();
    if (enabledConnectors.isEmpty()) {
      return;
    }

    for (ConnectorBase c : enabledConnectors) {
      try {
        if (!c.isRegistered()) {
          openCTIService.registerConnector(c);
        } else {
          openCTIService.pingConnector(c);
        }
        // Recovered (or never failed): reset the throttle so the next real failure warns at once.
        lastRegisterFailureWarnAt.remove(c.getId());
      } catch (Exception e) {
        logRegisterOrPingFailureThrottled(c, e);
      }
    }
  }

  /**
   * Logs a connector register/ping failure at most once per {@link #REGISTER_FAILURE_WARN_BACKOFF}
   * per connector: a concise WARN (message only) instead of a full ERROR stack every cycle. The
   * full stack stays available at DEBUG for troubleshooting, and genuine, non-transient failures
   * still surface (one WARN per backoff window) rather than being hidden entirely.
   */
  private void logRegisterOrPingFailureThrottled(ConnectorBase connector, Exception e) {
    Instant now = Instant.now();
    Instant lastWarn = lastRegisterFailureWarnAt.get(connector.getId());
    boolean withinBackoff =
        lastWarn != null
            && Duration.between(lastWarn, now).compareTo(REGISTER_FAILURE_WARN_BACKOFF) < 0;
    if (withinBackoff) {
      log.debug("OpenCTI connector {} still failing to register or ping", connector.getName(), e);
      return;
    }
    lastRegisterFailureWarnAt.put(connector.getId(), now);
    log.warn(
        "OpenCTI connector {} (tenant {}) could not register or ping: {}. This is expected while"
            + " OpenCTI is unreachable or its token is not yet authorized; retrying every cycle,"
            + " next warning in at most {} min.",
        connector.getName(),
        connector.getTenantId(),
        conciseReason(e),
        REGISTER_FAILURE_WARN_BACKOFF.toMinutes());
  }

  /** First non-blank line of the failure message, or the exception type when it carries none. */
  private static String conciseReason(Throwable e) {
    String message = e.getMessage();
    if (message == null || message.isBlank()) {
      return e.getClass().getSimpleName();
    }
    return message.strip().lines().findFirst().orElse(message.strip());
  }

  public void pushSecurityCoverageStixBundle(Bundle bundle, final String tenantId)
      throws ConnectorError, IOException {
    Optional<ConnectorBase> connector = getConnectorBase(tenantId);

    if (connector.isEmpty()) {
      throw new ConnectorError(
          "No instance of Security Coverage connector is currently active to send security coverage bundles for tenant id: "
              + tenantId);
    }

    openCTIService.pushStixBundle(bundle, connector.get());
  }

  public void acknowledgeReceivedOfCoverage(String workId, String message, String tenantId) {
    Optional<ConnectorBase> connector = getConnectorBase(tenantId);

    if (connector.isPresent()) {
      try {
        openCTIService.workToReceived(connector.get(), workId, message);
      } catch (Exception e) {
        log.error("workToReceived processing error", e);
      }
    }
  }

  public void acknowledgeProcessedOfCoverage(
      String workId, String message, Boolean inError, String tenantId) {
    Optional<ConnectorBase> connector = getConnectorBase(tenantId);

    if (connector.isPresent()) {
      try {
        openCTIService.workToProcessed(connector.get(), workId, message, inError);
      } catch (Exception e) {
        log.error("workToProcessed processing error", e);
      }
    }
  }
}
