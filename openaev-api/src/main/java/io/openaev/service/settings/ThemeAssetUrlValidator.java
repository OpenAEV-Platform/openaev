package io.openaev.service.settings;

import io.openaev.security.ssrf.PublicAddressPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards tenant-controlled theme asset urls (logo, login aside image, ...) against SSRF: only https
 * urls are allowed, and by default only those targeting a public address.
 *
 * <p>On-premise deployments in a closed network often have no public place to host a logo. They can
 * opt in with {@code openaev.theme.asset-allow-internal-targets}, the counterpart of {@code
 * openaev.notification.webhook-allow-internal-targets} for webhooks: it skips the check on internal
 * addresses only, the scheme and host checks still apply. It is off by default, so the platform
 * stays protected unless an operator who needs it turns it on knowingly.
 *
 * <p>This save-time check is only a first filter, not the security boundary: the asset url is
 * actually fetched later, for the headless browser that renders reports. There, {@code
 * ReportRenderEgressGuard} makes the browser fetch it through a client that resolves the host once
 * and connects only to the addresses it validated (public ones, unless the same opt-in is on), so a
 * host that now resolves differently (or answers differently to each lookup) or a redirect to an
 * internal target cannot get through. A host that fails to resolve here is let through for the same
 * reason {@code WebhookTargetValidator} lets it through: it cannot be used as-is, and DNS may
 * change by the time it is actually requested.
 *
 * <p>Urls saved before https became mandatory are not rewritten: they keep loading at render time,
 * still checked against internal addresses, until the theme is saved again.
 */
@Component
public class ThemeAssetUrlValidator {

  private final boolean allowInternalTargets;

  public ThemeAssetUrlValidator(
      @Value("${openaev.theme.asset-allow-internal-targets:false}") boolean allowInternalTargets) {
    this.allowInternalTargets = allowInternalTargets;
  }

  /** Validates a theme asset url, throwing {@link IllegalArgumentException} when not allowed. */
  public URI validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Theme asset url is not a valid URI");
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"https".equals(scheme)) {
      throw new IllegalArgumentException("Theme asset url must be https");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Theme asset url must have a host");
    }
    if (!this.allowInternalTargets) {
      requirePublicTarget(host);
    }
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
