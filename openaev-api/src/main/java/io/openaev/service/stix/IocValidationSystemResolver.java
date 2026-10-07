package io.openaev.service.stix;

import io.openaev.service.stix.IocValidationPlanner.HostResolver;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * The resolver of the OpenAEV server: the addresses the operating system resolves a host name to,
 * and an empty answer only when the DNS says that the name does not exist (NXDOMAIN).
 *
 * <p>The operating system reports a name that does not exist and a lookup that failed (a DNS server
 * that answered SERVFAIL or refused, none reachable in time) alike, with an {@link
 * UnknownHostException}. The name is then queried once on the DNS servers of the OpenAEV server,
 * which tell the two apart: only a NXDOMAIN answer is an empty answer. Any other outcome, a name
 * that exists without resolving included, fails the lookup, which {@link IocValidationHostAnswers}
 * records as not {@link HostResolver#answered answered}. A single-label name is never taken as
 * missing: the operating system also looked it up under its search domains.
 */
final class IocValidationSystemResolver implements HostResolver {

  /** Looks the addresses of a host name up, as {@link InetAddress#getAllByName} does. */
  @FunctionalInterface
  interface AddressLookup {
    InetAddress[] lookup(String host) throws UnknownHostException;
  }

  /** The wait for the first answer of a DNS server, doubled for the one retry. */
  static final Duration DNS_TIMEOUT = Duration.ofSeconds(1);

  /** The resolver of the OpenAEV server, with the DNS servers it is configured with. */
  static final IocValidationSystemResolver SERVER =
      new IocValidationSystemResolver(InetAddress::getAllByName, null, DNS_TIMEOUT);

  private static final String DNS_CONTEXT_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
  private static final String DNS_TIMEOUT_PROPERTY = "com.sun.jndi.dns.timeout.initial";
  private static final String DNS_RETRIES_PROPERTY = "com.sun.jndi.dns.timeout.retries";
  private static final String DNS_RETRIES = "1";

  private final AddressLookup addresses;
  private final String dnsServer;
  private final Duration dnsTimeout;

  /**
   * @param addresses the lookup of the operating system
   * @param dnsServer the {@code host:port} of the DNS server asked whether a name exists, or {@code
   *     null} for the DNS servers the OpenAEV server is configured with
   * @param dnsTimeout the wait for the first answer of that server
   */
  IocValidationSystemResolver(AddressLookup addresses, String dnsServer, Duration dnsTimeout) {
    this.addresses = addresses;
    this.dnsServer = dnsServer;
    this.dnsTimeout = dnsTimeout;
  }

  /**
   * @throws UncheckedIOException when the name does not resolve and the DNS does not say that it
   *     does not exist
   */
  @Override
  public List<InetAddress> resolve(String host) {
    try {
      return List.of(addresses.lookup(host));
    } catch (UnknownHostException e) {
      if (doesNotExist(host)) {
        return List.of();
      }
      throw new UncheckedIOException(
          "The DNS did not answer that '%s' does not exist".formatted(host), e);
    }
  }

  /** Whether a DNS server answered that the name does not exist (NXDOMAIN). */
  boolean doesNotExist(String host) {
    String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    if (name.indexOf('.') < 0) {
      return false;
    }
    Hashtable<String, String> environment = new Hashtable<>();
    environment.put(Context.INITIAL_CONTEXT_FACTORY, DNS_CONTEXT_FACTORY);
    if (dnsServer != null) {
      environment.put(Context.PROVIDER_URL, "dns://" + dnsServer);
    }
    environment.put(DNS_TIMEOUT_PROPERTY, String.valueOf(dnsTimeout.toMillis()));
    environment.put(DNS_RETRIES_PROPERTY, DNS_RETRIES);
    DirContext context = null;
    try {
      context = new InitialDirContext(environment);
      // An answer, even without an address, means the name exists
      context.getAttributes(name, new String[] {"A"});
      return false;
    } catch (NameNotFoundException e) {
      return true;
    } catch (NamingException e) {
      return false;
    } finally {
      if (context != null) {
        try {
          context.close();
        } catch (NamingException e) {
          // nothing held beyond the query
        }
      }
    }
  }
}
