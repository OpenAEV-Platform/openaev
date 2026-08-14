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
  private static final Duration REGISTER_OR_PING_BACKOFF = Duration.ofMinutes(5);

  @Getter private List<ConnectorBase> connectors = Collections.emptyList();
  private final XtmConfig xtmConfig;
  private final OpenAEVConfig openAEVConfig;
  private final OpenCTIService openCTIService;
  private final ConcurrentHashMap<String, Instant> registerOrPingBackoffUntil =
      new ConcurrentHashMap<>();

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
      String backoffKey = registerOrPingBackoffKey(c);
      Instant backoffUntil = registerOrPingBackoffUntil.get(backoffKey);
      if (backoffUntil != null && Instant.now().isBefore(backoffUntil)) {
        continue;
      }
      try {
        if (!c.isRegistered()) {
          openCTIService.registerConnector(c);
        } else {
          openCTIService.pingConnector(c);
        }
        registerOrPingBackoffUntil.remove(backoffKey);
      } catch (Exception e) {
        registerOrPingBackoffUntil.put(backoffKey, Instant.now().plus(REGISTER_OR_PING_BACKOFF));
        logRegisterOrPingFailure(c, e);
      }
    }
  }

  private static String registerOrPingBackoffKey(ConnectorBase connector) {
    return connector.getTenantId() + "/" + connector.getId();
  }

  private void logRegisterOrPingFailure(ConnectorBase connector, Exception e) {
    String detail =
        e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
    log.warn(
        "OpenCTI connector registration or ping failed for tenant {} / connector {} (retry in 5"
            + " minutes): {}",
        connector.getTenantId(),
        connector.getId(),
        detail);
  }

  void clearRegisterBackoff() {
    registerOrPingBackoffUntil.clear();
  }

  public void pushSecurityCoverageStixBundle(Bundle bundle, final String tenantId)
      throws ConnectorError, IOException {
    Optional<ConnectorBase> connector = getConnectorBase(tenantId);

    if (connector.isEmpty()) {
      throw new ConnectorError(
          "No instance of Security Coverage connector is currently active to send security coverage"
              + " bundles for tenant id: "
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
