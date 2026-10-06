package io.openaev.rest.reporting.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Route;
import io.openaev.security.ssrf.PublicAddressPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF guard for the headless render browser, applied at the real fetch boundary rather than only
 * at config-save time ({@code ThemeAssetUrlValidator}, {@code WebhookTargetValidator}).
 *
 * <p>The render page necessarily navigates a trusted-but-internal origin (loopback, or {@code
 * openaev.reporting.render-base-url}): a page-wide "block every internal address" rule cannot
 * apply here, it has to single out that one legitimate internal target and treat everything else -
 * most notably a tenant-controlled theme asset url rendered as an {@code <img>} - as untrusted.
 * Every other request is only allowed once its host is resolved, right now, to a verified public
 * address.
 *
 * <p>Installed as a Playwright request handler on every render {@link BrowserContext}: this re-
 * resolves the host at the moment Chromium is about to fetch it, which catches both a host that
 * resolved publicly when the url was saved but now resolves internally, and a redirect to an
 * internal target (Playwright routes every redirect hop as its own new request). Unlike {@code
 * WebhookTargetValidator}, there is no opt-out - report rendering has no legitimate reason to reach
 * an internal endpoint besides the platform's own origin, already allow-listed above.
 */
@Slf4j
@Component
public class ReportRenderEgressGuard {

  private final String trustedScheme;
  private final String trustedHost;
  private final int trustedPort;

  public ReportRenderEgressGuard(
      @Value("${openaev.reporting.render-base-url:}") final String renderBaseUrl,
      @Value("${server.port:8080}") final int serverPort,
      @Value("${server.ssl.enabled:false}") final boolean sslEnabled) {
    URI trustedOrigin = resolveTrustedOrigin(renderBaseUrl, serverPort, sslEnabled);
    this.trustedScheme = trustedOrigin.getScheme().toLowerCase(Locale.ROOT);
    this.trustedHost = trustedOrigin.getHost();
    this.trustedPort = effectivePort(this.trustedScheme, trustedOrigin.getPort());
  }

  /** Registers the guard on a fresh render context, for the lifetime of that context. */
  public void install(final BrowserContext context) {
    context.route("**/*", this::handle);
  }

  private void handle(final Route route) {
    String rawUrl = route.request().url();
    if (isAllowed(rawUrl)) {
      route.resume();
    } else {
      log.warn("Report render blocked a request to a disallowed target: {}", rawUrl);
      route.abort("blockedbyclient");
    }
  }

  /** Package-private so the allow/deny decision can be unit-tested without a real browser. */
  boolean isAllowed(final String rawUrl) {
    URI uri;
    try {
      uri = new URI(rawUrl);
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      // data:, blob:, about:blank, ... never reach the network - not an egress.
      return true;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return false;
    }
    if (scheme.equals(this.trustedScheme)
        && host.equalsIgnoreCase(this.trustedHost)
        && effectivePort(scheme, uri.getPort()) == this.trustedPort) {
      return true;
    }
    return isPublicHost(host);
  }

  private static boolean isPublicHost(final String host) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      // Fail closed: unlike webhook dispatch (where an unresolvable host simply fails the call
      // naturally), this guard also gates the platform's own trusted-origin traffic on the same
      // route, so an unresolvable non-trusted host is denied outright rather than let through.
      return false;
    }
    for (InetAddress address : addresses) {
      if (PublicAddressPolicy.isInternal(address)) {
        return false;
      }
    }
    return addresses.length > 0;
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
