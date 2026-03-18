package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtExtractor implements ExtractorBase {
  private final OpenCTIConnectorService openCTIConnectorService;

  @Override
  public String extractToken(String value) throws ConnectorError, JwtException {
    Optional<ConnectorBase> openCTIConnector = openCTIConnectorService.getConnectorBase();
    if (openCTIConnector.isEmpty()) {
      throw new ConnectorError("Connector not found");
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

    return openCTIConnector.get().getToken();
  }
}
