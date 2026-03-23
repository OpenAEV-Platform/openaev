package io.openaev.security;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.security.token.ConnectorJwtExtractor;
import io.openaev.security.token.ExtractorBase;
import io.openaev.security.token.PlainTokenExtractor;
import io.openaev.security.token.PlatformJwtExtractor;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String COOKIE_NAME = "openaev_token";
  private static final String HEADER_NAME = "Authorization";
  private static final String BEARER_PREFIX = "bearer ";

  private TokenRepository tokenRepository;
  private UserService userService;
  private ConnectorJwtExtractor connectorJwtExtractor;
  private PlainTokenExtractor plainTokenExtractor;
  private PlatformJwtExtractor platformJwtExtractor;

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @Autowired
  public void setConnectorJwtExtractor(ConnectorJwtExtractor connectorJwtExtractor) {
    this.connectorJwtExtractor = connectorJwtExtractor;
  }

  @Autowired
  public void setPlatformJwtExtractor(PlatformJwtExtractor platformJwtExtractor) {
    this.platformJwtExtractor = platformJwtExtractor;
  }

  @Autowired
  public void setPlainTokenExtractor(PlainTokenExtractor plainTokenExtractor) {
    this.plainTokenExtractor = plainTokenExtractor;
  }

  private String parseAuthorization(String value) {
    Set<ExtractorBase> extractors = Set.of(this.connectorJwtExtractor, this.platformJwtExtractor);

    if (!value.toLowerCase().startsWith(BEARER_PREFIX)) {
      return value;
    }

    String candidate = value.substring(BEARER_PREFIX.length());
    for (ExtractorBase extractor : extractors) {
      try {
        return extractor.extractToken(candidate);
      } catch (Exception e) {
        log.debug("Could not authenticate using extractor {}", extractor, e);
      }
    }
    return this.plainTokenExtractor.extractToken(candidate);
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
      } else if (userContext.getAuthentication() != null) {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
      }
    }
    filterChain.doFilter(request, response);
  }
}
