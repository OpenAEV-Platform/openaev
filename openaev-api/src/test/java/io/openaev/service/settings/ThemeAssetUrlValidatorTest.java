package io.openaev.service.settings;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Theme asset url validation (https only, internal targets off unless opted in)")
class ThemeAssetUrlValidatorTest {

  private final ThemeAssetUrlValidator validator = new ThemeAssetUrlValidator(false);

  // Operator opted in with openaev.theme.asset-allow-internal-targets (closed on-premise network).
  private final ThemeAssetUrlValidator onPremiseValidator = new ThemeAssetUrlValidator(true);

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "http://8.8.8.8/logo.png",
        "http://logo.example.com/logo.png",
        "file:///etc/passwd",
        "ftp://logo.example.com/logo.png",
        "//logo.example.com/logo.png",
        "/api/images/logo.png",
      })
  void rejects_anything_that_is_not_https(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
    assertThrows(IllegalArgumentException.class, () -> onPremiseValidator.validateUrl(url));
  }

  @Test
  void rejects_urls_without_a_host() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl("https://"));
    assertThrows(IllegalArgumentException.class, () -> onPremiseValidator.validateUrl("https://"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "https://127.0.0.1/logo.png",
        "https://localhost:8443/logo.png",
        "https://169.254.169.254/latest/meta-data",
        "https://10.0.0.5/logo.png",
        "https://192.168.1.10/logo.png",
        "https://172.16.0.1/logo.png",
      })
  void rejects_internal_targets_by_default(String url) {
    assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "https://127.0.0.1/logo.png",
        "https://localhost:8443/logo.png",
        "https://10.0.0.5/logo.png",
        "https://192.168.1.10/logo.png",
        "https://172.16.0.1/logo.png",
      })
  void allows_internal_targets_once_the_operator_opted_in(String url) {
    assertDoesNotThrow(() -> onPremiseValidator.validateUrl(url));
  }

  @Test
  void allows_public_targets() {
    assertDoesNotThrow(() -> validator.validateUrl("https://8.8.8.8/logo.png"));
    assertDoesNotThrow(() -> onPremiseValidator.validateUrl("https://8.8.8.8/logo.png"));
  }

  @Test
  void allows_unresolvable_hosts_at_save_time() {
    // The real security boundary is the report render's egress guard, re-checked at fetch time.
    assertDoesNotThrow(() -> validator.validateUrl("https://no-such-host.invalid/logo.png"));
  }
}
