package io.openaev.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
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
    Optional<SecurityCoverageConnector> openCTIConnector =
        openCTIConnectorService.getConnectorBase();
    if (openCTIConnector.isEmpty()) {
      throw new ServletException("Connector not found");
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
        .parseSignedClaims(jwt);
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
