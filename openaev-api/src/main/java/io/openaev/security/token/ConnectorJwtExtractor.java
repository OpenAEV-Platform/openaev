package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.config.TenantUriUtils;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
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
  public Optional<User> authUser(String value, HttpServletRequest request) throws ConnectorError, JwtException {
    Optional<String> tenantId = tenantUriUtils.getTenantIdFromRequestUrl(request);
    if(tenantId.isEmpty()) {
      throw new ConnectorError("Cannot locate a connector without a tenant ID in the request.");
    }

    Optional<ConnectorBase> connector = openCTIConnectorService.getConnectorBase(tenantId.get());
    if (connector.isEmpty()) {
      throw new ConnectorError("Connector for tenant '%s' not found".formatted(tenantId.get()));
    }

    try {
      Jwts.parser()
          .requireIssuer("opencti")
          .requireSubject("connector")
          .keyLocator(
              header -> {
                String kid = (String) header.get("kid");
                return Jwks.setParser().build().parse(connector.get().getJwks()).getKeys().stream()
                    .filter(k -> kid.equals(k.getId()))
                    .findFirst()
                    .orElseThrow()
                    .toKey();
              })
          .build()
          .parseSignedClaims(value);
      return userService.findByTokenAndTenantId(connector.get().getToken(), connector.get().getTenantId());
    } catch (Exception e) {
      // No exception needed here because thrown above
    }

    throw new ConnectorError("Token or JWT not valid");
  }
}
