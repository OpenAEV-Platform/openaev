package io.openaev.security.ssrf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/**
 * Apache HttpClient {@link DnsResolver} that only ever hands out public addresses.
 *
 * <p>Checking a host and then letting the HTTP client resolve it again leaves a gap (DNS
 * rebinding): a hostile DNS server answers a public address to the check and an internal one to the
 * connection. Plugging this resolver into the client closes that gap, because the addresses the
 * client connects to are exactly the ones validated here - there is a single resolution, and the
 * connection cannot use any other answer.
 *
 * <p>A host is refused as soon as ONE of its addresses is internal (see {@link
 * PublicAddressPolicy}): the client may pick any of them, so a mixed answer is not safe. Refusal is
 * reported as an {@link UnknownHostException}, which is what makes the client abort the request.
 */
public final class PublicOnlyDnsResolver implements DnsResolver {

  private final HostResolver delegate;

  public PublicOnlyDnsResolver() {
    this(InetAddress::getAllByName);
  }

  /** The delegate only decides how a name is resolved, never which addresses are acceptable. */
  public PublicOnlyDnsResolver(final HostResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    InetAddress[] addresses = this.delegate.resolve(host);
    if (addresses == null || addresses.length == 0) {
      throw new UnknownHostException("Host " + host + " does not resolve to any address");
    }
    for (InetAddress address : addresses) {
      if (PublicAddressPolicy.isInternal(address)) {
        throw new UnknownHostException(
            "Host " + host + " resolves to a private or internal address, which is not allowed");
      }
    }
    return addresses;
  }

  @Override
  public String resolveCanonicalHostname(final String host) {
    return host;
  }
}
