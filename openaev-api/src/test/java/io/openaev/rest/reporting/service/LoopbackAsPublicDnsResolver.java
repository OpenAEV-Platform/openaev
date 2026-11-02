package io.openaev.rest.reporting.service;

import io.openaev.security.ssrf.PublicOnlyDnsResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.DnsResolver;

/**
 * Test resolver standing in for DNS: the chosen names are "public" yet actually reach the local
 * test server, every other host goes through the real {@link PublicOnlyDnsResolver}, so internal
 * targets (loopback literals, {@code localhost}, ...) are refused exactly as in production.
 */
final class LoopbackAsPublicDnsResolver implements DnsResolver {

  private final Set<String> publicNames;
  private final PublicOnlyDnsResolver strict = new PublicOnlyDnsResolver();
  private final AtomicInteger lookups = new AtomicInteger();

  LoopbackAsPublicDnsResolver(final String... publicNames) {
    this.publicNames = Set.of(publicNames);
  }

  /** How many times the resolver was asked to resolve a host, whatever the host. */
  int lookups() {
    return this.lookups.get();
  }

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    this.lookups.incrementAndGet();
    if (this.publicNames.contains(host)) {
      return new InetAddress[] {InetAddress.getByAddress(host, new byte[] {127, 0, 0, 1})};
    }
    return this.strict.resolve(host);
  }

  @Override
  public String resolveCanonicalHostname(final String host) {
    return host;
  }
}
