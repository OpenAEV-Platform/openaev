package io.openaev.security.token;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.config.TenantUriUtils;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectorJwtExtractor implements ExtractorBase {
  private final OpenCTIConnectorService openCTIConnectorService;
  private final UserService userService;
  private final TenantUriUtils tenantUriUtils;

  @Override
  public Optional<User> authUser(String value, HttpServletRequest request)
      throws ConnectorError, JwtException {
    // if no tenant can be found in the request URL,
    // backwards compatibility fallback to default tenant ID
    String tenantId = tenantUriUtils.getTenantIdFromRequestUrl(request).orElse(DEFAULT_TENANT_UUID);

    Optional<ConnectorBase> connector = openCTIConnectorService.getConnectorBase(tenantId);
    Optional<ConnectorBase> iocValidationConnector =
        openCTIConnectorService.getIocValidationConnector(tenantId);
    if (connector.isEmpty() && iocValidationConnector.isEmpty()) {
      throw new ConnectorError("Connector for tenant '%s' not found".formatted(tenantId));
    }

    // Both connectors of a tenant authenticate with the tenant OpenCTI token; the IOC validation
    // connector's key set only matters when the coverage connector has none to verify with.
    for (Optional<ConnectorBase> candidate : List.of(connector, iocValidationConnector)) {
      if (candidate.isPresent() && verifies(candidate.get(), value, tenantId)) {
        return userService.findByTokenAndTenantId(
            candidate.get().getToken(), candidate.get().getTenantId());
      }
    }

    throw new ConnectorError("Token or JWT not valid");
  }

  private boolean verifies(ConnectorBase connector, String value, String tenantId) {
    if (connector.getJwks() == null) {
      return false;
    }
    try {
      Jwts.parser()
          .requireIssuer("opencti")
          .requireSubject("connector")
          .keyLocator(
              header -> {
                String kid = (String) header.get("kid");
                return Jwks.setParser().build().parse(connector.getJwks()).getKeys().stream()
                    .filter(k -> kid.equals(k.getId()))
                    .findFirst()
                    .orElseThrow()
                    .toKey();
              })
          .build()
          .parseSignedClaims(value);
      return true;
    } catch (Exception e) {
      log.debug(
          "Connector JWT verification failed for tenant {} / connector {}",
          tenantId,
          connector.getId(),
          e);
      return false;
    }
  }
}
