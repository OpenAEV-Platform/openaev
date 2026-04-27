package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.context.TenantContext;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.UserService;
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

  @Override
  public Optional<User> authUser(String value) throws ConnectorError, JwtException {
    String tenantId = TenantContext.getCurrentTenant();
    if (tenantId == null || tenantId.isBlank()) {
      throw new ConnectorError(
          "Tenant context not established — cannot authenticate connector JWT");
    }

    Optional<ConnectorBase> openCTIConnector = openCTIConnectorService.getConnectorBase(tenantId);
    if (openCTIConnector.isEmpty()) {
      throw new ConnectorError("Connector not found for tenant");
    }

    Jwts.parser()
        .requireIssuer("opencti")
        .requireSubject("connector")
        .keyLocator(
            header -> {
              String kid = (String) header.get("kid");
              return Jwks.setParser()
                  .build()
                  .parse(openCTIConnector.get().getJwks())
                  .getKeys()
                  .stream()
                  .filter(k -> kid.equals(k.getId()))
                  .findFirst()
                  .orElseThrow()
                  .toKey();
            })
        .build()
        .parseSignedClaims(value);

    return userService.findByTokenAndTenantId(openCTIConnector.get().getToken(), tenantId);
  }
}
