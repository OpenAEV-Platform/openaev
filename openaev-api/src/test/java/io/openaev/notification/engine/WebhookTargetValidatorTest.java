package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Webhook target validation (SSRF guard)")
class WebhookTargetValidatorTest {

  private final WebhookTargetValidator validator = new WebhookTargetValidator(false);
  private final WebhookTargetValidator permissiveValidator = new WebhookTargetValidator(true);

  @Test
  void rejects_non_http_schemes() {
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("ftp://example.org/hook"));
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl("file:///etc/passwd"));
  }

  @Test
  void rejects_urls_without_a_host() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl("http://"));
  }

  @Test
  void rejects_loopback_targets() {
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("http://127.0.0.1/hook"));
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("http://localhost:8080/hook"));
  }

  @Test
  void rejects_link_local_metadata_targets() {
    assertThrows(
        IllegalArgumentException.class,
        () -> validator.validateUrl("http://169.254.169.254/latest/meta-data"));
  }

  @Test
  void rejects_private_range_targets() {
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("http://10.0.0.5/hook"));
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("http://192.168.1.10/hook"));
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateUrl("http://172.16.0.1/hook"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://[::1]/hook",
        "http://[fc00::1]/hook",
        "http://[fd12:3456::1]/hook",
        "http://[fe80::1]/hook",
        "http://[::]/hook",
      })
  void rejects_ipv6_internal_ranges(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // Each address below writes an internal IPv4 address in an IPv6 form.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        // NAT64, well-known prefix 64:ff9b::/96
        "http://[64:ff9b::7f00:1]/hook",
        "http://[64:ff9b::a9fe:a9fe]/hook",
        "http://[64:ff9b::ac10:fe01]/hook",
        "http://[64:ff9b::c0a8:101]/hook",
        // NAT64, local prefix 64:ff9b:1::/48
        "http://[64:ff9b:1::7f00:1]/hook",
        "http://[64:ff9b:1::a9fe:a9fe]/hook",
        // 6to4, 2002::/16
        "http://[2002:7f00:0001::]/hook",
        "http://[2002:c0a8:0101::]/hook",
        "http://[2002:a9fe:a9fe::]/hook",
        // Teredo, 2001:0::/32, embedded address is bitwise inverted
        "http://[2001:0:0:0:0:0:80ff:fffe]/hook",
        "http://[2001:0:0:0:0:0:5601:5601]/hook",
        // IPv4-compatible, deprecated but still parsed
        "http://[::7f00:1]/hook",
        "http://[::a9fe:a9fe]/hook",
        "http://[::c0a8:101]/hook",
        // ISATAP, IPv4 in the interface identifier
        "http://[2001:db8::5efe:7f00:1]/hook",
        "http://[2001:db8::200:5efe:a9fe:a9fe]/hook",
      })
  void rejects_ipv6_addresses_wrapping_an_internal_ipv4(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // IANA special-purpose ranges the JDK reports as ordinary addresses.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://100.64.0.1/hook",
        "http://100.127.255.254/hook",
        "http://0.0.0.5/hook",
        "http://192.0.0.1/hook",
        "http://198.18.0.1/hook",
        "http://198.19.255.254/hook",
        "http://240.0.0.1/hook",
        "http://255.255.255.255/hook",
      })
  void rejects_reserved_ipv4_ranges(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // The reserved ranges above must stay rejected once wrapped in an IPv6 carrier.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://[64:ff9b::6440:1]/hook",
        "http://[2002:6440:0001::]/hook",
        "http://[::6440:1]/hook",
        "http://[64:ff9b::c000:1]/hook",
      })
  void rejects_ipv6_wrapping_a_reserved_ipv4(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // Any prefix inside 64:ff9b::/32, not only the two IANA-assigned ones.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://[64:ff9b:2::7f00:1]/hook",
        "http://[64:ff9b:ffff::a9fe:a9fe]/hook",
      })
  void rejects_nat64_range_beyond_the_assigned_prefixes(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // The JDK returns an Inet4Address for these. Pinned so a change to the IPv6 handling
  // cannot regress them.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://[::ffff:127.0.0.1]/hook",
        "http://[::ffff:7f00:1]/hook",
        "http://[::ffff:a9fe:a9fe]/hook",
        "http://2130706433/hook",
      })
  void rejects_ipv4_mapped_and_decimal_forms(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  // Public targets must stay reachable, including when they are written in a transition form.
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://[64:ff9b::808:808]/hook",
        "http://[2002:0808:0808::]/hook",
        "http://[64:ff9b:1::808:808]/hook",
        "http://[2606:4700::1111]/hook",
        "http://8.8.8.8/hook",
      })
  void allows_public_targets(String url) {
    assertDoesNotThrow(() -> validator.validateUrl(url));
  }

  @Test
  void allows_internal_targets_when_configured() {
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://127.0.0.1/hook"));
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://10.0.0.5/hook"));
  }

  @Test
  void allows_wrapped_internal_targets_when_configured() {
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://[64:ff9b::7f00:1]/hook"));
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://[::a9fe:a9fe]/hook"));
  }

  @Test
  void allows_unresolvable_hosts() {
    // No request can ever reach an unresolvable host - dispatch fails naturally
    assertDoesNotThrow(() -> validator.validateUrl("https://no-such-host.invalid/webhook"));
  }

  @Test
  void verb_defaults_to_post_and_is_normalized() {
    assertEquals("POST", validator.validateVerb(null));
    assertEquals("POST", validator.validateVerb(" "));
    assertEquals("PUT", validator.validateVerb("put"));
  }

  @Test
  void rejects_unsupported_verbs() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateVerb("TRACE"));
    assertThrows(IllegalArgumentException.class, () -> validator.validateVerb("CONNECT"));
  }
}
