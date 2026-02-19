package io.openaev.security;

import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

public class OpenCTIJwtAuthenticationFilter extends OncePerRequestFilter {
  private UserService userService;
  private OpenCTIConnectorService openCTIConnectorService;

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setOpenCTIConnectorService(OpenCTIConnectorService openCTIConnectorService) {
    this.openCTIConnectorService = openCTIConnectorService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // only runs for /api/stix/** — skipped for everything else
    return !request.getRequestURI().startsWith("/api/stix/");
  }

  /**
   * Function used to validate JWT token with OpenCTI jwks
   *
   * @param jwt JWT token to validate
   * @throws Exception if token not valid
   */
  public void validateOpenCTIJwt(String jwt) throws Exception {
    Optional<ConnectorBase> openCTIConnector = openCTIConnectorService.getConnectorBase();
    if (openCTIConnector.isEmpty()) {
      throw new ServletException("Connector not found");
    }

    // Parse JWT first to extract the kid from header
    SignedJWT signedJWT = SignedJWT.parse(jwt);
    String kid = signedJWT.getHeader().getKeyID();
    if (kid == null) {
      throw new Exception("JWT header does not contain a kid");
    }

    // Parse JWKS and get jwk key by kid
    String jwksJson = ((SecurityCoverageConnector) openCTIConnector.get()).getJwks();
    JWKSet jwkSet = JWKSet.parse(jwksJson);
    JWK jwk = jwkSet.getKeyByKeyId(kid);
    if (jwk == null) {
      throw new Exception("No key found in JWKS for kid: " + kid);
    }
    if (!(jwk instanceof OctetKeyPair okpKey)) {
      throw new Exception("Key with kid " + kid + " is not an OKP key");
    }

    // Verify signature
    Ed25519Verifier verifier = new Ed25519Verifier(okpKey.toPublicJWK());
    if (!signedJWT.verify(verifier)) {
      throw new Exception("JWT signature verification failed");
    }

    // Validate Expiration date
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
    if (claims.getExpirationTime() != null
        && claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
      throw new Exception("JWT token has expired");
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer")) {
      filterChain.doFilter(request, response);
      return;
    }
    String token = authHeader.substring("Bearer ".length()).trim();

    try {
      validateOpenCTIJwt(token);
      this.userService.createAdminSession();
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }

    filterChain.doFilter(request, response);
  }
}
