package io.openaev.service.settings;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Theme asset url validation (SSRF guard, no internal-target opt-out)")
class ThemeAssetUrlValidatorTest {

  private final ThemeAssetUrlValidator validator = new ThemeAssetUrlValidator();

  @Test
  void rejects_non_http_schemes() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl("file:///etc/passwd"));
  }

  @Test
  void rejects_urls_without_a_host() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl("http://"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://127.0.0.1/logo.png",
        "http://localhost:8080/logo.png",
        "http://169.254.169.254/latest/meta-data",
        "http://10.0.0.5/logo.png",
        "http://192.168.1.10/logo.png",
        "http://172.16.0.1/logo.png",
      })
  void rejects_internal_targets_with_no_opt_out(String url) {
    // Unlike WebhookTargetValidator, there is no configuration flag that can let this through.
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  @Test
  void allows_public_targets() {
    assertDoesNotThrow(() -> validator.validateUrl("https://8.8.8.8/logo.png"));
  }

  @Test
  void allows_unresolvable_hosts_at_save_time() {
    // The real security boundary is the report render's egress guard, re-checked at fetch time.
    assertDoesNotThrow(() -> validator.validateUrl("https://no-such-host.invalid/logo.png"));
  }
}
