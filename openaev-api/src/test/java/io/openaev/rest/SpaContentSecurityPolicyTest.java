package io.openaev.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SPA content security policy")
class SpaContentSecurityPolicyTest {

  @Test
  @DisplayName("given many responses should use a different 128-bit hex nonce each time")
  void given_manyResponses_should_useDifferentHexNonces() {
    // Act
    Set<String> nonces = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      nonces.add(SpaContentSecurityPolicy.newNonce());
    }

    // Assert
    assertEquals(1000, nonces.size());
    assertTrue(nonces.stream().allMatch(nonce -> nonce.matches("[0-9a-f]{32}")));
  }

  @Test
  @DisplayName("given a nonce should allow inline scripts for this nonce only")
  void given_nonce_should_allowInlineScriptForThisNonceOnly() {
    // Act
    String scriptSrc = directive(SpaContentSecurityPolicy.policy("abc123", null), "script-src");

    // Assert
    assertTrue(scriptSrc.contains("'nonce-abc123'"));
    assertFalse(scriptSrc.contains("'unsafe-inline'"));
    assertFalse(scriptSrc.contains("'unsafe-eval'"));
  }

  @Test
  @DisplayName("given an XTM Hub url should let the browser connect to its origin only")
  void given_xtmHubUrl_should_allowConnectionToItsOriginOnly() {
    // Act
    String policy =
        SpaContentSecurityPolicy.policy("n", "https://hub.example.io:8443/some/path?x=1");

    // Assert
    assertEquals(
        "connect-src 'self' https://hub.example.io:8443", directive(policy, "connect-src"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "javascript:alert(1)",
        "ftp://hub.example.io",
        "not a url",
        "https://user@hub.example.io",
        "https://hub.example.io; script-src *",
        "https://hub.example.io\r\nSet-Cookie: a=b"
      })
  @DisplayName(
      "given an XTM Hub url that is not a plain http(s) origin should not widen the policy")
  void given_unsafeXtmHubUrl_should_notWidenThePolicy(String url) {
    // Act
    String policy = SpaContentSecurityPolicy.policy("n", url);

    // Assert
    assertNull(SpaContentSecurityPolicy.originOf(url));
    assertEquals("connect-src 'self'", directive(policy, "connect-src"));
  }

  private static String directive(String policy, String name) {
    return Arrays.stream(policy.split("; "))
        .filter(d -> d.equals(name) || d.startsWith(name + " "))
        .findFirst()
        .orElseThrow(() -> new AssertionError("directive " + name + " missing from " + policy));
  }
}
