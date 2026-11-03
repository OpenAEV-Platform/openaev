package io.openaev.rest.reporting.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Route;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF guard for the headless render browser, applied at the real fetch boundary rather than only
 * at config-save time ({@code ThemeAssetUrlValidator}, {@code WebhookTargetValidator}).
 *
 * <p>The render page necessarily navigates a trusted-but-internal origin (loopback, or {@code
 * openaev.reporting.render-base-url}): a page-wide "block every internal address" rule cannot apply
 * here, it has to single out that one legitimate internal target and treat everything else - most
 * notably a tenant-controlled theme asset url rendered as an {@code <img>} - as untrusted.
 *
 * <p>Installed as a Playwright request handler on every render {@link BrowserContext}, it sorts
 * each request into:
 *
 * <ul>
 *   <li>trusted: the platform's own origin, which the browser reaches directly, with the render
 *       token attached;
 *   <li>external: anything else. The browser never connects to it. {@link ExternalResourceFetcher}
 *       fetches it, resolving the host once and only to public addresses, and the response is
 *       handed back to the browser. Resolving and connecting in one place is what rules out DNS
 *       rebinding: a name checked here and resolved again by the browser could answer differently
 *       the second time. Redirects are followed by that fetch too, each hop validated: a redirect
 *       handed back to the browser would be followed by it without going through this guard;
 *   <li>blocked: what cannot even be parsed as an http(s) target.
 * </ul>
 *
 * <p>Report rendering has no reason to reach an internal endpoint besides the platform's own
 * origin, so by default external fetches are refused internal addresses. On-premise networks with
 * no public place to host a logo can opt in with {@code
 * openaev.theme.asset-allow-internal-targets}, which applies to every external fetch of the render,
 * not only to the theme urls.
 *
 * <p>The render token is deliberately NOT a context-wide header: Chromium would replay such a
 * header on every request of the context, handing the token to whoever controls a tenant-supplied
 * logo url. Instead the guard attaches the {@code Authorization} header itself, and only on
 * requests to the trusted origin (an allow-list rather than a strip-list). External requests are
 * made by {@link ExternalResourceFetcher}, which never forwards credentials. A tenant-controlled
 * host therefore cannot borrow the token through a redirect either: the redirect is followed by
 * that fetch, which reaches the trusted origin like any other host (loopback is refused, a publicly
 * resolvable origin is fetched anonymously).
 *
 * <p>Known limit: a request the browser itself makes to the trusted origin carries the token
 * whoever referenced it. A tenant url that points straight at that origin could thus have the
 * platform's own API requested as an image with the render token. Saved theme urls must be public,
 * so this needs a publicly resolvable {@code openaev.reporting.render-base-url} - or the opt-in to
 * internal targets, which also lets a theme url name the render origin itself. The response is only
 * ever rendered as an image, never readable by the tenant.
 */
@Slf4j
@Component
public class ReportRenderEgressGuard {

  private final String trustedScheme;
  private final String trustedHost;
  private final int trustedPort;
  private final ExternalResourceFetcher externalResourceFetcher;

  public ReportRenderEgressGuard(
      @Value("${openaev.reporting.render-base-url:}") final String renderBaseUrl,
      @Value("${server.port:8080}") final int serverPort,
      @Value("${server.ssl.enabled:false}") final boolean sslEnabled,
      final ExternalResourceFetcher externalResourceFetcher) {
    URI trustedOrigin = resolveTrustedOrigin(renderBaseUrl, serverPort, sslEnabled);
    this.trustedScheme = trustedOrigin.getScheme().toLowerCase(Locale.ROOT);
    this.trustedHost = trustedOrigin.getHost();
    this.trustedPort = effectivePort(this.trustedScheme, trustedOrigin.getPort());
    this.externalResourceFetcher = externalResourceFetcher;
  }

  /**
   * Registers the guard on a fresh render context, for the lifetime of that context.
   *
   * @param trustedAuthorization the {@code Authorization} header value to attach to requests to the
   *     trusted origin, or null to attach nothing
   */
  public void install(final BrowserContext context, final String trustedAuthorization) {
    context.route("**/*", route -> handle(route, trustedAuthorization));
  }

  /** Package-private so the resume/fulfill/abort behavior can be unit-tested. */
  void handle(final Route route, final String trustedAuthorization) {
    String rawUrl = route.request().url();
    switch (classify(rawUrl)) {
      case BLOCKED -> {
        log.warn("Report render blocked a request to a disallowed target: {}", rawUrl);
        route.abort("blockedbyclient");
      }
      case TRUSTED -> resumeWithAuthorization(route, trustedAuthorization);
      case EXTERNAL -> fulfillFromExternalFetch(route, rawUrl);
    }
  }

  private static void resumeWithAuthorization(final Route route, final String authorization) {
    if (authorization == null) {
      route.resume();
      return;
    }
    // ResumeOptions.headers replaces the whole header set, so start from the request's own.
    Map<String, String> headers = new HashMap<>(route.request().headers());
    headers.keySet().removeIf("authorization"::equalsIgnoreCase);
    headers.put("authorization", authorization);
    route.resume(new Route.ResumeOptions().setHeaders(headers));
  }

  private void fulfillFromExternalFetch(final Route route, final String rawUrl) {
    String method = route.request().method().toUpperCase(Locale.ROOT);
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      // A render only ever loads resources. Anything that writes has no business leaving.
      log.warn("Report render blocked a {} request to an external target: {}", method, rawUrl);
      route.abort("blockedbyclient");
      return;
    }
    ExternalResourceFetcher.FetchedResource resource;
    try {
      resource = this.externalResourceFetcher.fetch(method, rawUrl, route.request().headers());
    } catch (UnknownHostException e) {
      // Unresolvable, or resolving to an internal address: refused by the resolver.
      log.warn("Report render blocked a request to {}: {}", rawUrl, e.getMessage());
      route.abort("blockedbyclient");
      return;
    } catch (IOException | RuntimeException e) {
      log.warn("Report render could not fetch {}: {}", rawUrl, e.toString());
      route.abort("failed");
      return;
    }
    route.fulfill(
        new Route.FulfillOptions()
            .setStatus(resource.status())
            .setHeaders(resource.headers())
            .setBodyBytes(resource.body()));
  }

  /**
   * Package-private so the trusted/external distinction can be unit-tested without a real browser.
   */
  Verdict classify(final String rawUrl) {
    URI uri;
    try {
      uri = new URI(rawUrl);
    } catch (URISyntaxException e) {
      return Verdict.BLOCKED;
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      // data:, blob:, about:blank, ... never reach the network - not an egress.
      return Verdict.TRUSTED;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return Verdict.BLOCKED;
    }
    if (scheme.equals(this.trustedScheme)
        && host.equalsIgnoreCase(this.trustedHost)
        && effectivePort(scheme, uri.getPort()) == this.trustedPort) {
      return Verdict.TRUSTED;
    }
    return Verdict.EXTERNAL;
  }

  /**
   * Egress verdict for a single request: block it, let the browser reach the platform with the
   * render token, or fetch it on the browser's behalf.
   */
  enum Verdict {
    BLOCKED,
    TRUSTED,
    EXTERNAL
  }

  private static URI resolveTrustedOrigin(
      final String renderBaseUrl, final int serverPort, final boolean sslEnabled) {
    try {
      if (renderBaseUrl != null && !renderBaseUrl.isBlank()) {
        return new URI(renderBaseUrl.trim());
      }
      String scheme = sslEnabled ? "https" : "http";
      return new URI(scheme + "://localhost:" + serverPort);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          "Invalid openaev.reporting.render-base-url: " + renderBaseUrl, e);
    }
  }

  private static int effectivePort(final String scheme, final int port) {
    if (port != -1) {
      return port;
    }
    return "https".equalsIgnoreCase(scheme) ? 443 : 80;
  }
}
