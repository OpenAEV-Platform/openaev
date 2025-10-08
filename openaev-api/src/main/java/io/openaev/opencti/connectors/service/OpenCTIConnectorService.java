package io.openaev.opencti.connectors.service;

import io.openaev.opencti.client.mutations.PushStixBundle;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenCTIConnectorService {
  @Getter private final List<ConnectorBase> connectors;
  private final OpenCTIService openCTIService;
  private final PrivilegeService privilegeService;

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
      } catch (ConnectorError e) {
        log.warn("An error occurred in the backend.", e);
      } catch (IOException e) {
        log.warn(
            "A technical error occurred while registering connector {} with OpenCTI at {}",
            c.getName(),
            c.getUrl(),
            e);
      }
    }
  }

  public PushStixBundle.ResponsePayload pushSecurityCoverageStixBundle(Bundle bundle)
      throws ConnectorError, IOException {
    Optional<ConnectorBase> connector =
        connectors.stream().filter(c -> c instanceof SecurityCoverageConnector).findFirst();
    if (connector.isEmpty() || !connector.get().isRegistered()) {
      throw new ConnectorError(
          "Security Coverage connector is not ready to send security coverage bundles.");
    }

    return openCTIService.sendSecurityCoverageStixBundle(bundle, connector.get());
  }
}
