package io.openaev.security;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.jsonwebtoken.JwtException;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.security.token.JwtExtractor;
import io.openaev.security.token.PlainTokenExtractor;
import io.openaev.service.UserService;
import io.openaev.xtmone.XtmOneConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Log
public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String COOKIE_NAME = "openaev_token";
  private static final String HEADER_NAME = "Authorization";
  private static final String BEARER_PREFIX = "bearer ";
  private static final Set<String> TRUSTED_ISSUERS = Set.of("filigran-copilot");

  private TokenRepository tokenRepository;
  private UserService userService;
  private JwtExtractor jwtExtractor;
  private PlainTokenExtractor plainTokenExtractor;
  private XtmOneConfig xtmOneConfig;

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setJwtExtractor(JwtExtractor jwtExtractor) {
    this.jwtExtractor = jwtExtractor;
  }

  @Autowired
  public void setPlainTokenExtractor(PlainTokenExtractor plainTokenExtractor) {
    this.plainTokenExtractor = plainTokenExtractor;
  }

  @Autowired
  public void setXtmOneConfig(XtmOneConfig xtmOneConfig) {
    this.xtmOneConfig = xtmOneConfig;
  }

  private String parseAuthorization(String value) {
    if (value.toLowerCase().startsWith(BEARER_PREFIX)) {
      String candidate = value.substring(BEARER_PREFIX.length());
      try {
        return this.jwtExtractor.extractToken(candidate);
      } catch (ConnectorError | JwtException | IllegalArgumentException | NullPointerException e) {
        return this.plainTokenExtractor.extractToken(candidate);
      }
    }
    return value;
  }

  private String getRawBearer(HttpServletRequest request) {
    String header = request.getHeader(HEADER_NAME);
    if (hasLength(header) && header.toLowerCase().startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private String getAuthToken(HttpServletRequest request) {
    String header = request.getHeader(HEADER_NAME);
    Cookie[] cookies = ofNullable(request.getCookies()).orElse(new Cookie[0]);
    Optional<Cookie> defaultCookie =
        Arrays.stream(cookies).filter(cookie -> COOKIE_NAME.equals(cookie.getName())).findFirst();
    return hasLength(header)
        ? parseAuthorization(header)
        : defaultCookie.orElseGet(() -> new Cookie(COOKIE_NAME, null)).getValue();
  }

  private User tryPlatformManagedJwt(String rawBearer) {
    if (rawBearer == null) {
      log.warning("[XTM One Auth] No raw bearer token found");
      return null;
    }
    if (xtmOneConfig == null || !xtmOneConfig.isConfigured()) {
      log.warning("[XTM One Auth] XTM One not configured, skipping platform JWT check");
      return null;
    }
    String secret = xtmOneConfig.getToken();
    if (secret == null || secret.isBlank()) {
      log.warning("[XTM One Auth] XTM One token is blank");
      return null;
    }
    log.warning(
        "[XTM One Auth] Trying platform-managed JWT validation (bearer length="
            + rawBearer.length()
            + ")");
    try {
      String[] parts = rawBearer.split("\\.");
      if (parts.length != 3) {
        log.warning(
            "[XTM One Auth] Bearer is not a valid JWT (expected 3 parts, got "
                + parts.length
                + ")");
        return null;
      }
      // Verify HMAC-SHA256 signature
      byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
      String signingInput = parts[0] + "." + parts[1];
      byte[] computed = mac.doFinal(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String expectedSig =
          java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(computed);
      if (!expectedSig.equals(parts[2])) {
        log.warning("[XTM One Auth] JWT HMAC signature mismatch");
        return null;
      }
      // Decode payload
      String payloadJson =
          new String(
              java.util.Base64.getUrlDecoder().decode(parts[1]),
              java.nio.charset.StandardCharsets.UTF_8);
      var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
      String issuer = payload.has("iss") ? payload.get("iss").asText() : null;
      if (issuer == null || !TRUSTED_ISSUERS.contains(issuer)) {
        log.warning(
            "[XTM One Auth] JWT issuer '" + issuer + "' not in trusted set " + TRUSTED_ISSUERS);
        return null;
      }
      String email = payload.has("email") ? payload.get("email").asText() : null;
      if (email == null || email.isBlank()) {
        log.warning("[XTM One Auth] JWT from '" + issuer + "' missing email claim");
        return null;
      }
      log.warning("[XTM One Auth] JWT valid, issuer=" + issuer + ", email=" + email);
      Optional<User> user = userService.findByEmailIgnoreCase(email);
      if (user.isPresent()) {
        log.warning("[XTM One Auth] Authenticated platform-managed request for " + email);
        return user.get();
      }
      log.warning("[XTM One Auth] No user found for email: " + email);
    } catch (Exception e) {
      log.warning("[XTM One Auth] Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return null;
  }

  @Override
  @SuppressWarnings("NullableProblems")
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authToken = getAuthToken(request);
    if (authToken != null) {
      Optional<Token> token = tokenRepository.findByValue(authToken);
      SecurityContext userContext = SecurityContextHolder.getContext();
      if (token.isPresent()) {
        User user = token.get().getUser();
        userService.createUserSession(user);
      } else {
        User platformUser = tryPlatformManagedJwt(getRawBearer(request));
        if (platformUser != null) {
          userService.createUserSession(platformUser);
        } else if (userContext.getAuthentication() != null) {
          SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
