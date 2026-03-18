package io.openaev.opencti.connectors.service;

import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.stix.objects.Bundle;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenCTIConnectorService {
  @Getter private final List<ConnectorBase> connectors;
  private final OpenCTIService openCTIService;

  @NotNull
  public Optional<ConnectorBase> getConnectorBase() {
    // don't examine the bundle
    // pick the first occurrence of the correct connector type
    // it's not supported yet to have more than one active connector of each type
    return connectors.stream()
        .filter(c -> c instanceof SecurityCoverageConnector && c.shouldRegister())
        .findFirst();
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
      } catch (Exception e) {
        log.error("Error at OpenCTI connector registration or ping", e);
      }
    }
  }

  public void pushSecurityCoverageStixBundle(Bundle bundle) throws ConnectorError, IOException {
    Optional<ConnectorBase> connector = getConnectorBase();

    if (connector.isEmpty()) {
      throw new ConnectorError(
          "No instance of Security Coverage connector is currently active to send security coverage bundles.");
    }

    openCTIService.pushStixBundle(bundle, connector.get());
  }

  public void acknowledgeReceivedOfCoverage(String workId, String message) {
    Optional<ConnectorBase> connector = getConnectorBase();

    if (connector.isPresent()) {
      try {
        openCTIService.workToReceived(connector.get(), workId, message);
      } catch (Exception e) {
        log.error("workToReceived processing error", e);
      }
    }
  }

  public void acknowledgeProcessedOfCoverage(String workId, String message, Boolean inError) {
    Optional<ConnectorBase> connector = getConnectorBase();

    if (connector.isPresent()) {
      try {
        openCTIService.workToProcessed(connector.get(), workId, message, inError);
      } catch (Exception e) {
        log.error("workToProcessed processing error", e);
      }
    }
  }
}
