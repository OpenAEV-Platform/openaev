package io.openaev.rest;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prevents search engines from crawling and indexing the platform.
 *
 * <p>Three layers of protection:
 *
 * <ol>
 *   <li>A {@code <meta name="robots">} tag in index.html (frontend build)
 *   <li>A global {@code X-Robots-Tag} HTTP header on every response (this filter)
 *   <li>Explicit {@code /robots.txt} and {@code /sitemap.xml} routes (this controller)
 * </ol>
 */
@RestController
public class CrawlerProtectionApi {

  static final String NO_INDEX_DIRECTIVES = "noindex, nofollow, noarchive, nosnippet, noimageindex";

  static final String X_ROBOTS_TAG_HEADER = "X-Robots-Tag";

  static final String ROBOTS_TXT_BODY = "User-agent: *\nDisallow: /\n";

  static final String EMPTY_SITEMAP =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"></urlset>
      """;

  // -- robots.txt: disallow all crawlers on every path
  @GetMapping(path = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<String> robotsTxt(TxCtx ctx) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
        .body(ROBOTS_TXT_BODY);
  }

  // -- sitemap.xml: return an empty sitemap to discourage indexing
  @GetMapping(path = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<String> sitemapXml(TxCtx ctx) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
        .body(EMPTY_SITEMAP);
  }

  /**
   * Registers a servlet filter that adds the {@code X-Robots-Tag} header to every HTTP response.
   * This reinforces the {@code <meta name="robots">} tag at the HTTP level.
   */
  @Bean
  public FilterRegistrationBean<Filter> xRobotsTagFilter() {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new Filter() {
          @Override
          public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
              throws IOException, ServletException {
            if (response instanceof HttpServletResponse httpResponse) {
              httpResponse.setHeader(X_ROBOTS_TAG_HEADER, NO_INDEX_DIRECTIVES);
            }
            chain.doFilter(request, response);
          }
        });
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
