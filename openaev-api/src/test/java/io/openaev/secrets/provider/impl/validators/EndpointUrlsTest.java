package io.openaev.secrets.provider.impl.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EndpointUrls tests")
class EndpointUrlsTest {

  @ParameterizedTest(name = "\"{0}\" is accepted")
  @ValueSource(
      strings = {
        "https://login.sovereign.example",
        "https://login.sovereign.example/token",
        "http://127.0.0.1:4566",
        "HTTPS://LOGIN.SOVEREIGN.EXAMPLE/"
      })
  @DisplayName("An absolute HTTP(S) URL with a host is accepted")
  void given_absoluteHttpUrl_should_beAccepted(String value) {
    // Act & Assert
    assertThat(EndpointUrls.requireHttpUri(value)).hasToString(value);
    assertThat(EndpointUrls.isMalformedOverride(value)).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is refused")
  @ValueSource(
      strings = {
        "/token",
        "token",
        "//login.sovereign.example/token",
        "file:///tmp/token",
        "ftp://login.sovereign.example/token",
        "https://",
        "https:///token",
        "https://login.sovereign.example:abc",
        "http://not a host/token"
      })
  @DisplayName("A relative reference, an unsupported scheme or a missing host is refused")
  void given_notAnAbsoluteHttpUrl_should_beRefused(String value) {
    // Act & Assert
    assertThatThrownBy(() -> EndpointUrls.requireHttpUri(value))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(EndpointUrls.isMalformedOverride(value)).isTrue();
  }

  @ParameterizedTest(name = "[{0}] is not an override")
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  @DisplayName("An absent override is not malformed: it means the official endpoint")
  void given_noOverride_should_notBeMalformed(String value) {
    // Act & Assert
    assertThat(EndpointUrls.isMalformedOverride(value)).isFalse();
  }
}
