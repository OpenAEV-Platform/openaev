package io.openaev.security;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String COOKIE_NAME = "openaev_token";
  private static final String HEADER_NAME = "Authorization";
  private static final String BEARER_PREFIX = "bearer ";
  private TokenRepository tokenRepository;
  private UserService userService;

  @Autowired
  public void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  private String parseAuthorization(String value) {
    if (value.toLowerCase().startsWith(BEARER_PREFIX)) {
      return value.substring(BEARER_PREFIX.length());
    }
    return value;
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
    // Extract from request
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
