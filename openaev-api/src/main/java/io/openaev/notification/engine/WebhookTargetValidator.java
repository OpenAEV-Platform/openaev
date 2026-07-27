package io.openaev.notification.engine;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards webhook notifiers against SSRF: only http(s) URLs targeting public addresses are allowed,
 * with a small allow-list of HTTP verbs. Applied both when a notifier is saved and again at
 * dispatch time (the configuration is raw JSON and could bypass the API-time check).
 *
 * <p>On-premise deployments that legitimately post to internal endpoints can opt out of the
 * private-address restriction with {@code openaev.notification.webhook-allow-internal-targets}.
 */
@Component
public class WebhookTargetValidator {

  private static final Set<String> ALLOWED_VERBS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

  private final boolean allowInternalTargets;

  public WebhookTargetValidator(
      @Value("${openaev.notification.webhook-allow-internal-targets:false}")
          boolean allowInternalTargets) {
    this.allowInternalTargets = allowInternalTargets;
  }

  /** Validates a webhook URL, throwing {@link IllegalArgumentException} when it is not allowed. */
  public URI validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Webhook notifier url is not a valid URI");
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Webhook notifier url must be http(s)");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Webhook notifier url must have a host");
    }
    if (!allowInternalTargets) {
      requirePublicTarget(host);
    }
    return uri;
  }

  /** Validates the webhook HTTP verb, defaulting to POST when blank. */
  public String validateVerb(String verb) {
    String normalized =
        verb == null || verb.isBlank() ? "POST" : verb.trim().toUpperCase(Locale.ROOT);
    if (!ALLOWED_VERBS.contains(normalized)) {
      throw new IllegalArgumentException("Webhook notifier verb must be one of " + ALLOWED_VERBS);
    }
    return normalized;
  }

  private void requirePublicTarget(String host) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      // An unresolvable host cannot be requested at all - let the dispatch fail naturally.
      return;
    }
    for (InetAddress address : addresses) {
      if (isInternal(address)) {
        throw new IllegalArgumentException(
            "Webhook notifier url resolves to a private or internal address, which is not"
                + " allowed on this platform");
      }
    }
  }

  // Loopback, link-local (incl. 169.254.169.254 cloud metadata), RFC1918 site-local, wildcard,
  // multicast and IPv6 unique-local (fc00::/7) ranges are considered internal.
  private static boolean isInternal(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    if (address instanceof Inet6Address) {
      byte firstByte = address.getAddress()[0];
      return (firstByte & 0xFE) == 0xFC;
    }
    return false;
  }
}
