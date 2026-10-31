package io.openaev.authorisation;

import java.net.ProxySelector;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.UnaryOperator;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Route planner that sends requests through the proxy the platform runs behind.
 *
 * <p>The JVM proxy properties ({@code https.proxyHost}, {@code http.nonProxyHosts}...) are honored
 * first, through the default {@link ProxySelector}. When they designate no proxy, the {@code
 * HTTPS_PROXY} / {@code HTTP_PROXY} environment variables apply, minus the hosts listed in {@code
 * NO_PROXY}: the same contract as the Azure SDK's own transport, which this planner replaces for
 * the calls routed through Apache.
 */
public class EnvironmentProxyRoutePlanner extends SystemDefaultRoutePlanner {

  private final UnaryOperator<String> environment;

  public EnvironmentProxyRoutePlanner() {
    this(System::getenv);
  }

  EnvironmentProxyRoutePlanner(UnaryOperator<String> environment) {
    super(ProxySelector.getDefault());
    this.environment = environment;
  }

  @Override
  protected HttpHost determineProxy(HttpHost target, HttpContext context) {
    try {
      HttpHost fromSystem = super.determineProxy(target, context);
      if (fromSystem != null) {
        return fromSystem;
      }
    } catch (Exception e) {
      // No usable JVM proxy configuration: the environment variables remain.
    }
    if (isExcluded(target.getHostName())) {
      return null;
    }
    String proxyUrl =
        "https".equalsIgnoreCase(target.getSchemeName())
            ? firstDefined("HTTPS_PROXY", "https_proxy")
            : firstDefined("HTTP_PROXY", "http_proxy");
    if (proxyUrl == null) {
      return null;
    }
    URI uri = URI.create(proxyUrl.contains("://") ? proxyUrl : "http://" + proxyUrl);
    int port = uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
    return new HttpHost(uri.getScheme(), uri.getHost(), port);
  }

  private boolean isExcluded(String host) {
    String noProxy = firstDefined("NO_PROXY", "no_proxy");
    if (noProxy == null) {
      return false;
    }
    String lowerHost = host.toLowerCase(Locale.ROOT);
    return Arrays.stream(noProxy.split(","))
        .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
        .filter(entry -> !entry.isEmpty())
        .anyMatch(
            entry -> {
              if ("*".equals(entry)) {
                return true;
              }
              String domain = entry.startsWith(".") ? entry.substring(1) : entry;
              return lowerHost.equals(domain) || lowerHost.endsWith("." + domain);
            });
  }

  private String firstDefined(String... names) {
    for (String name : names) {
      String value = environment.apply(name);
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }
}
