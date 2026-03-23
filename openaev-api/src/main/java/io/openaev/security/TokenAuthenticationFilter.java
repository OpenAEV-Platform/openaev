package io.openaev.security;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.jsonwebtoken.JwtException;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.security.token.ConnectorJwtExtractor;
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

  private TokenRepository tokenRepository;
  private UserService userService;
  private ConnectorJwtExtractor connectorJwtExtractor;
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
  public void setJwtExtractor(ConnectorJwtExtractor connectorJwtExtractor) {
    this.connectorJwtExtractor = connectorJwtExtractor;
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
        return this.connectorJwtExtractor.extractToken(candidate);
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
