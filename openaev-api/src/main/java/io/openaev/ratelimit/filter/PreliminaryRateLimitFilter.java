package io.openaev.ratelimit.filter;

import static io.openaev.utils.HttpReqRespUtils.getClientIpAddressFromRequest;

import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.ratelimit.model.RateLimitedPrincipal;
import io.openaev.ratelimit.service.RateLimitService;
import io.openaev.ratelimit.store.Limit;
import io.openaev.ratelimit.store.request.LimitConsumptionRequest;
import io.openaev.ratelimit.store.request.LimitSpecification;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class PreliminaryRateLimitFilter extends OncePerRequestFilter {
  private final RateLimitService rateLimitService;
  private final RateLimitConfig config;
  private final UserService userService;

  public PreliminaryRateLimitFilter(
      RateLimitService rateLimitService, RateLimitConfig config, UserService userService) {
    this.rateLimitService = rateLimitService;
    this.config = config;
    this.userService = userService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    SecurityContext ctx = SecurityContextHolder.getContext();

    boolean authenticated =
        ctx.getAuthentication() != null && ctx.getAuthentication().isAuthenticated();

    if (!authenticated) {
      LimitSpecification spec = new LimitSpecification(config.getDefaultRps());
      LimitConsumptionRequest lcr =
          new LimitConsumptionRequest(
              new RateLimitedPrincipal(getClientIpAddressFromRequest(request)),
              "all endpoints",
              spec);

      Limit l = rateLimitService.consume(lcr);

      if (l.getIsRateLimited()) {
        response.setHeader("RateLimit-Limit", config.getDefaultRps().toString());
        response.setHeader("RateLimit-Remaining", l.getRemaining().toString());
        response.setHeader("RateLimit-Reset", l.getReset().toString());
        response.sendError(HttpStatus.SC_TOO_MANY_REQUESTS, "Rate limited.");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }
}
