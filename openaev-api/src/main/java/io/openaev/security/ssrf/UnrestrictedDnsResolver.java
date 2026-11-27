package io.openaev.security.ssrf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/**
 * Apache HttpClient {@link DnsResolver} that hands out whatever a host resolves to, internal
 * addresses included. Counterpart of {@link PublicOnlyDnsResolver}, for the on-premise deployments
 * that opted in to internal targets (see {@code openaev.theme.asset-allow-internal-targets}): never
 * wire it in without that explicit opt-in.
 *
 * <p>Allowing internal addresses does not change how the lookup behaves: the host is still resolved
 * once, and the client connects to the addresses returned here and no other, so a host cannot
 * answer differently to a second lookup.
 */
public final class UnrestrictedDnsResolver implements DnsResolver {

  private final HostResolver delegate;

  public UnrestrictedDnsResolver(final HostResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    InetAddress[] addresses = this.delegate.resolve(host);
    if (addresses == null || addresses.length == 0) {
      throw new UnknownHostException("Host " + host + " does not resolve to any address");
    }
    return addresses;
  }

  @Override
  public String resolveCanonicalHostname(final String host) {
    return host;
  }
}
