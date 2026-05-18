package io.openaev.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Global HTTP filter that sets the X-Robots-Tag header on every response to prevent search engines
 * from indexing the platform.
 *
 * <p>This complements the {@code <meta name="robots">} tag in index.html and the dedicated
 * /robots.txt and /sitemap.xml endpoints provided by {@link
 * io.openaev.rest.CrawlerPreventionApi}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RobotsNoIndexFilter extends OncePerRequestFilter {

  private static final String X_ROBOTS_TAG = "X-Robots-Tag";
  private static final String NO_INDEX_DIRECTIVES =
      "noindex, nofollow, noarchive, nosnippet, noimageindex";

  @Override
  @SuppressWarnings("NullableProblems")
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    response.setHeader(X_ROBOTS_TAG, NO_INDEX_DIRECTIVES);
    filterChain.doFilter(request, response);
  }
}
