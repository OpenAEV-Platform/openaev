package io.openaev.security.ssrf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DNS resolver handing out public addresses only (closes the DNS rebinding gap)")
class PublicOnlyDnsResolverTest {

  private static InetAddress ip(final String literal) throws UnknownHostException {
    return InetAddress.getByName(literal);
  }

  private static PublicOnlyDnsResolver resolverAnswering(final InetAddress... answer) {
    return new PublicOnlyDnsResolver(host -> answer);
  }

  @Test
  void hands_out_the_addresses_it_validated() throws Exception {
    InetAddress[] answer = {ip("8.8.8.8"), ip("2606:4700::1111")};

    assertArrayEquals(answer, resolverAnswering(answer).resolve("logo.example.com"));
  }

  @Test
  void refuses_a_host_resolving_to_an_internal_address() {
    assertThrows(
        UnknownHostException.class,
        () -> resolverAnswering(ip("127.0.0.1")).resolve("rebinding.example.com"));
    assertThrows(
        UnknownHostException.class,
        () -> resolverAnswering(ip("169.254.169.254")).resolve("metadata.example.com"));
  }

  @Test
  void refuses_a_host_as_soon_as_one_of_its_addresses_is_internal() {
    // The client may connect to any address of the answer, so a mixed answer is not safe.
    assertThrows(
        UnknownHostException.class,
        () -> resolverAnswering(ip("8.8.8.8"), ip("10.0.0.5")).resolve("mixed.example.com"));
  }

  @Test
  void refuses_an_ipv6_address_wrapping_an_internal_ipv4_address() {
    assertThrows(
        UnknownHostException.class,
        () -> resolverAnswering(ip("64:ff9b::7f00:1")).resolve("nat64.example.com"));
  }

  @Test
  void refuses_an_empty_answer() {
    assertThrows(
        UnknownHostException.class,
        () -> new PublicOnlyDnsResolver(host -> new InetAddress[0]).resolve("empty.example.com"));
  }

  @Test
  void lets_an_unresolvable_host_fail_as_such() {
    PublicOnlyDnsResolver resolver =
        new PublicOnlyDnsResolver(
            host -> {
              throw new UnknownHostException(host);
            });

    assertThrows(UnknownHostException.class, () -> resolver.resolve("no-such-host.invalid"));
  }
}
