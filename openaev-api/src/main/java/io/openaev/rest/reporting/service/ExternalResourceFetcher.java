package io.openaev.rest.reporting.service;

import io.openaev.security.ssrf.HostResolver;
import io.openaev.security.ssrf.PublicOnlyDnsResolver;
import io.openaev.security.ssrf.TimeoutHostResolver;
import io.openaev.security.ssrf.UnrestrictedDnsResolver;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fetches, on behalf of the render browser, the resources it loads from hosts other than the
 * platform itself (a tenant-controlled logo url, a web font, ...).
 *
 * <p>The browser must not connect to those hosts on its own: it would resolve the name a second
 * time, after {@link ReportRenderEgressGuard} validated it, and a hostile DNS server could answer
 * an internal address to that second resolution (DNS rebinding). Here the request is made by an
 * HTTP client whose {@link DnsResolver} validates the very addresses it then connects to ({@link
 * PublicOnlyDnsResolver}: public addresses only, unless the operator opted in to internal targets
 * with {@code openaev.theme.asset-allow-internal-targets}), so there is no gap to exploit. The
 * guard hands the response back to the browser.
 *
 * <p>Redirects are followed here, never by the browser: a 3xx handed back to Chromium is followed
 * by Chromium itself, straight to wherever {@code Location} points and without going through the
 * guard again. Following them in this client sends every hop through the same resolver, so each hop
 * is validated and connected to on its own, and a redirect to an internal target fails like any
 * direct request to it. The {@code Location} header is dropped from what is handed back, so the
 * browser is left with nothing to follow.
 *
 * <p>The client carries no cookies, no credentials and no proxy configuration, and forwards only
 * the request headers that make sense for a resource fetch. Nothing it does may hold the browser
 * driver's dispatch thread for long: connection, reads, the whole body and the DNS lookup are each
 * bounded, and a lookup that exceeds its timeout fails the fetch like an unresolvable host.
 */
@Component
public class ExternalResourceFetcher {

  /** Largest resource handed back to the browser: a logo or a font, never a download. */
  static final int DEFAULT_MAX_BODY_BYTES = 10 * 1024 * 1024;

  private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);
  private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(15);

  /**
   * How long a host lookup may block. The fetch runs on the browser driver's dispatch thread, which
   * serves every request of the render: an unbounded lookup (the operating system resolver gives up
   * after many seconds) would stall them all.
   */
  private static final Duration DNS_LOOKUP_TIMEOUT = Duration.ofSeconds(3);

  /** Lookups allowed to wait on DNS at once; beyond that they are refused, not queued. */
  private static final int MAX_CONCURRENT_DNS_LOOKUPS = 8;

  /** Wall-clock budget for reading a whole body: the socket timeout only bounds each read. */
  private static final Duration BODY_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Request headers never forwarded: connection management (set by the client itself), anything
   * that could carry credentials, and the encoding negotiation the client handles on its own.
   */
  private static final Set<String> NOT_FORWARDED_REQUEST_HEADERS =
      Set.of(
          "host",
          "connection",
          "keep-alive",
          "content-length",
          "transfer-encoding",
          "accept-encoding",
          "te",
          "upgrade",
          "expect",
          "authorization",
          "proxy-authorization",
          "cookie");

  /**
   * Response headers dropped: framing the browser recomputes, cookies it must never store, and the
   * redirect target, which is this client's business and never the browser's.
   */
  private static final Set<String> NOT_FORWARDED_RESPONSE_HEADERS =
      Set.of(
          "connection",
          "keep-alive",
          "transfer-encoding",
          "content-length",
          "content-encoding",
          "set-cookie",
          "set-cookie2",
          "location");

  /** Redirect hops followed before giving up, each one validated like the first request. */
  private static final int MAX_REDIRECTS = 5;

  /** What was fetched, ready to be handed back to the browser. */
  record FetchedResource(int status, Map<String, String> headers, byte[] body) {}

  private final CloseableHttpClient client;
  private final int maxBodyBytes;

  /**
   * @param allowInternalTargets {@code openaev.theme.asset-allow-internal-targets}: when on, hosts
   *     resolving to internal addresses are fetched like any other (on-premise networks with no
   *     public place to host a logo). Off by default.
   */
  @Autowired
  public ExternalResourceFetcher(
      @Value("${openaev.theme.asset-allow-internal-targets:false}")
          final boolean allowInternalTargets) {
    this(dnsResolver(allowInternalTargets), DEFAULT_MAX_BODY_BYTES);
  }

  private static DnsResolver dnsResolver(final boolean allowInternalTargets) {
    HostResolver boundedLookup =
        new TimeoutHostResolver(
            InetAddress::getAllByName, DNS_LOOKUP_TIMEOUT, MAX_CONCURRENT_DNS_LOOKUPS);
    return allowInternalTargets
        ? new UnrestrictedDnsResolver(boundedLookup)
        : new PublicOnlyDnsResolver(boundedLookup);
  }

  /** Package-private so tests can reach a local server and use a small size limit. */
  ExternalResourceFetcher(final DnsResolver dnsResolver, final int maxBodyBytes) {
    this.maxBodyBytes = maxBodyBytes;
    this.client =
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(dnsResolver)
                    .setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setConnectTimeout(CONNECT_TIMEOUT)
                            .setSocketTimeout(SOCKET_TIMEOUT)
                            .build())
                    .setDefaultTlsConfig(
                        TlsConfig.custom().setHandshakeTimeout(CONNECT_TIMEOUT).build())
                    .build())
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(MAX_REDIRECTS)
                    .build())
            .disableCookieManagement()
            .disableAutomaticRetries()
            .build();
  }

  /**
   * Fetches a resource, resolving its host exactly once and only to public addresses.
   *
   * @param method {@code GET} or {@code HEAD}
   * @param url the absolute http(s) url
   * @param requestHeaders the headers the browser would have sent
   * @throws java.net.UnknownHostException when the host resolves to an internal address, or not at
   *     all
   * @throws IOException when the resource cannot be fetched, or is too large or too slow
   */
  FetchedResource fetch(
      final String method, final String url, final Map<String, String> requestHeaders)
      throws IOException {
    ClassicHttpRequest request = ClassicRequestBuilder.create(method).setUri(url).build();
    requestHeaders.forEach(
        (name, value) -> {
          if (!NOT_FORWARDED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))
              && !name.startsWith(":")) {
            request.addHeader(name, value);
          }
        });
    return this.client.execute(
        request,
        response -> {
          Map<String, String> headers = new HashMap<>();
          for (Header header : response.getHeaders()) {
            String name = header.getName().toLowerCase(Locale.ROOT);
            if (!NOT_FORWARDED_RESPONSE_HEADERS.contains(name)) {
              headers.merge(name, header.getValue(), (previous, next) -> previous + ", " + next);
            }
          }
          return new FetchedResource(response.getCode(), headers, readBody(response.getEntity()));
        });
  }

  private byte[] readBody(final HttpEntity entity) throws IOException {
    if (entity == null) {
      return new byte[0];
    }
    if (entity.getContentLength() > this.maxBodyBytes) {
      throw new IOException("Resource is larger than " + this.maxBodyBytes + " bytes");
    }
    long deadline = System.nanoTime() + BODY_TIMEOUT.toNanos();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    try (InputStream in = entity.getContent()) {
      int read;
      while ((read = in.read(chunk)) != -1) {
        if (body.size() + read > this.maxBodyBytes) {
          throw new IOException("Resource is larger than " + this.maxBodyBytes + " bytes");
        }
        body.write(chunk, 0, read);
        if (System.nanoTime() > deadline) {
          throw new IOException("Resource took longer than " + BODY_TIMEOUT.toSeconds() + "s");
        }
      }
    }
    return body.toByteArray();
  }

  @PreDestroy
  void close() throws IOException {
    this.client.close();
  }
}
