package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.UserService;
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

  @Override
  public Optional<User> authUser(String value) throws ConnectorError, JwtException {
    List<ConnectorBase> connectors = openCTIConnectorService.getRegisterConnectors();
    if (connectors.isEmpty()) {
      throw new ConnectorError("Connectors not found");
    }
    for (ConnectorBase connector : connectors) {
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
        return userService.findByTokenAndTenantId(connector.getToken(), connector.getTenantId());
      } catch (Exception e) {
        // No exception needed here because thrown above
      }
    }
    throw new ConnectorError("Token or JWT not valid");
  }
}
