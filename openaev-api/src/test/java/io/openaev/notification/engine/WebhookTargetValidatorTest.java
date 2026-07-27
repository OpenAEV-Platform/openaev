package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

  @Test
  void allows_internal_targets_when_configured() {
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://127.0.0.1/hook"));
    assertDoesNotThrow(() -> permissiveValidator.validateUrl("http://10.0.0.5/hook"));
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
