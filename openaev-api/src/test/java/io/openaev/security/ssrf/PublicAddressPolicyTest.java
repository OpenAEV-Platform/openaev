package io.openaev.security.ssrf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Public address classification shared by every SSRF guard")
class PublicAddressPolicyTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "127.0.0.1",
        "10.0.0.5",
        "192.168.1.10",
        "172.16.0.1",
        "169.254.169.254",
        "0.0.0.5",
        "100.64.0.1",
        "192.0.0.1",
        "198.18.0.1",
        "240.0.0.1",
        "255.255.255.255",
      })
  void classifies_internal_and_reserved_ipv4_addresses_as_internal(String ip) throws Exception {
    assertTrue(PublicAddressPolicy.isInternal(address(ip)));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"8.8.8.8", "1.1.1.1"})
  void classifies_public_ipv4_addresses_as_public(String ip) throws Exception {
    assertFalse(PublicAddressPolicy.isInternal(address(ip)));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"::1", "fc00::1", "fd12:3456::1", "fe80::1", "::"})
  void classifies_internal_ipv6_addresses_as_internal(String ip) throws Exception {
    assertTrue(PublicAddressPolicy.isInternal(address(ip)));
  }

  @Test
  void classifies_a_public_ipv6_address_as_public() throws Exception {
    assertFalse(PublicAddressPolicy.isInternal(address("2606:4700::1111")));
  }

  // IPv6 forms embedding an internal IPv4 address must resolve through the embedded address.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "64:ff9b::7f00:1", // NAT64 wrapping 127.0.0.1
        "::ffff:127.0.0.1", // IPv4-mapped wrapping 127.0.0.1
        "2002:a9fe:a9fe::", // 6to4 wrapping 169.254.169.254
      })
  void classifies_ipv6_wrapping_an_internal_ipv4_as_internal(String ip) throws Exception {
    assertTrue(PublicAddressPolicy.isInternal(address(ip)));
  }

  @Test
  void classifies_an_ipv6_wrapping_a_public_ipv4_as_public() throws Exception {
    assertFalse(PublicAddressPolicy.isInternal(address("64:ff9b::808:808")));
  }

  private static InetAddress address(String ip) throws UnknownHostException {
    return InetAddress.getByName(ip);
  }
}
