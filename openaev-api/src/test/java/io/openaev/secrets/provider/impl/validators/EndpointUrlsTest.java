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
        "http://not a host/token",
        "https://user:secret@login.sovereign.example/token",
        "https://login.sovereign.example/token?tenant=other",
        "https://login.sovereign.example/token#section"
      })
  @DisplayName(
      "A relative reference, an unsupported scheme, a missing host, credentials, a query or a fragment is refused")
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

  @ParameterizedTest(name = "\"{0}\" targets an internal address")
  @ValueSource(
      strings = {
        "http://127.0.0.1:4566",
        "http://localhost:4566/token",
        "https://10.0.0.5/",
        "https://172.16.3.4/",
        "https://192.168.1.10/",
        "http://169.254.169.254/latest/meta-data/",
        "http://0.0.0.0:8080",
        "http://100.64.0.1/",
        "http://[::1]:8080/",
        "http://[fd00::1]/",
        "http://[::ffff:127.0.0.1]/"
      })
  @DisplayName("A loopback, private, link-local or reserved host is internal")
  void given_internalHost_should_targetAnInternalAddress(String value) {
    // Act & Assert
    assertThat(EndpointUrls.targetsInternalAddress(value)).isTrue();
  }

  @ParameterizedTest(name = "\"{0}\" does not target an internal address")
  @ValueSource(
      strings = {
        "https://8.8.8.8/",
        "https://93.184.216.34:8443/token",
        "https://[2606:4700:4700::1111]/",
        // Does not resolve (RFC 6761): the call fails by itself, the check has nothing to refuse.
        "https://login.sovereign.invalid"
      })
  @DisplayName("A public host, or one that does not resolve, is not internal")
  void given_publicOrUnresolvableHost_should_notTargetAnInternalAddress(String value) {
    // Act & Assert
    assertThat(EndpointUrls.targetsInternalAddress(value)).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not looked up")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "/token", "http://not a host/token", "file:///tmp/token"})
  @DisplayName("An absent or unparsable override is left to the malformed check")
  void given_absentOrUnparsableOverride_should_notTargetAnInternalAddress(String value) {
    // Act & Assert
    assertThat(EndpointUrls.targetsInternalAddress(value)).isFalse();
  }

  @ParameterizedTest(name = "opt-out={0}")
  @ValueSource(booleans = {true, false})
  @DisplayName("The on-premise opt-out is the only thing that lets an internal host through")
  void given_internalHost_should_beRefusedUnlessOptedOut(boolean allowInternal) {
    // Act & Assert
    assertThat(EndpointUrls.isRefused("http://10.0.0.5/", allowInternal)).isEqualTo(!allowInternal);
    assertThat(EndpointUrls.isRefused("https://8.8.8.8/", allowInternal)).isFalse();
    assertThat(EndpointUrls.isRefused(null, allowInternal)).isFalse();
  }
}
