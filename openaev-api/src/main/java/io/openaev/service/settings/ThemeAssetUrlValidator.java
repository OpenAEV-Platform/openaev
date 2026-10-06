package io.openaev.service.settings;

import io.openaev.security.ssrf.PublicAddressPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Guards tenant-controlled theme asset urls (logo, login aside image, ...) against SSRF: only
 * http(s) urls targeting a public address are allowed.
 *
 * <p>Deliberately independent from {@link io.openaev.notification.engine.WebhookTargetValidator}:
 * {@code openaev.notification.webhook-allow-internal-targets} exists for on-premise deployments
 * that legitimately dispatch webhooks to internal endpoints an admin configured. There is no
 * equivalent legitimate case for a theme asset, so this validator has no opt-out.
 *
 * <p>This save-time check is only a first filter, not the security boundary: the asset url is
 * actually fetched later, by the headless browser that renders reports, which is where {@code
 * ReportRenderEgressGuard} re-resolves the host at fetch time (catching a host that now resolves
 * differently, or a redirect to an internal target) and is the mechanism that cannot be bypassed. A
 * host that fails to resolve here is let through for the same reason {@code WebhookTargetValidator}
 * lets it through: it cannot be used as-is, and DNS may change by the time it is actually
 * requested.
 */
@Component
public class ThemeAssetUrlValidator {

  /** Validates a theme asset url, throwing {@link IllegalArgumentException} when not allowed. */
  public URI validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Theme asset url is not a valid URI");
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Theme asset url must be http(s)");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Theme asset url must have a host");
    }
    requirePublicTarget(host);
    return uri;
  }

  private void requirePublicTarget(String host) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      return;
    }
    for (InetAddress address : addresses) {
      if (PublicAddressPolicy.isInternal(address)) {
        throw new IllegalArgumentException(
            "Theme asset url resolves to a private or internal address, which is not allowed on"
                + " this platform");
      }
    }
  }
}
