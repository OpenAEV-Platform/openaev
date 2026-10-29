package io.openaev.security.ssrf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DNS resolver for operators who opted in to internal targets")
class UnrestrictedDnsResolverTest {

  @Test
  void hands_out_internal_addresses_too() throws Exception {
    InetAddress[] answer = {InetAddress.getByName("10.0.0.5"), InetAddress.getByName("127.0.0.1")};

    UnrestrictedDnsResolver resolver = new UnrestrictedDnsResolver(host -> answer);

    assertArrayEquals(answer, resolver.resolve("logo.intranet.example.com"));
  }

  @Test
  void still_refuses_an_empty_answer() {
    UnrestrictedDnsResolver resolver = new UnrestrictedDnsResolver(host -> new InetAddress[0]);

    assertThrows(UnknownHostException.class, () -> resolver.resolve("empty.example.com"));
  }

  @Test
  void lets_an_unresolvable_host_fail_as_such() {
    UnrestrictedDnsResolver resolver =
        new UnrestrictedDnsResolver(
            host -> {
              throw new UnknownHostException(host);
            });

    assertThrows(UnknownHostException.class, () -> resolver.resolve("no-such-host.invalid"));
  }
}
