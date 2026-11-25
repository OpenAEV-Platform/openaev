package io.openaev.secrets.provider.impl.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.SecretReference.SECRET_STATUS;
import io.openaev.security.ssrf.HostResolver;
import io.openaev.security.ssrf.TimeoutHostResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

  private static final int BUDGET_SECONDS = 5;

  private static SECRET_STATUS refusedStatus(boolean allowInternal, String endpoint) {
    return EndpointUrls.refusal(allowInternal, BUDGET_SECONDS, endpoint)
        .map(result -> result.status())
        .orElse(null);
  }

  @ParameterizedTest(name = "\"{0}\" is refused")
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
  @DisplayName("A loopback, private, link-local or reserved host is refused as a format error")
  void given_internalHost_should_beRefused(String value) {
    // Act & Assert
    assertThat(refusedStatus(false, value)).isEqualTo(SECRET_STATUS.FORMAT_ERROR);
  }

  @ParameterizedTest(name = "\"{0}\" is let through")
  @ValueSource(
      strings = {
        "https://8.8.8.8/",
        "https://93.184.216.34:8443/token",
        "https://[2606:4700:4700::1111]/",
        // Does not resolve (RFC 6761): the call fails by itself, the check has nothing to refuse.
        "https://login.sovereign.invalid"
      })
  @DisplayName("A public host, or one that does not resolve, is let through")
  void given_publicOrUnresolvableHost_should_beLetThrough(String value) {
    // Act & Assert
    assertThat(refusedStatus(false, value)).isNull();
  }

  @ParameterizedTest(name = "\"{0}\" is not looked up")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "/token", "http://not a host/token", "file:///tmp/token"})
  @DisplayName("An absent or unparsable override is left to the malformed check")
  void given_absentOrUnparsableOverride_should_beLetThrough(String value) {
    // Act & Assert
    assertThat(refusedStatus(false, value)).isNull();
  }

  @Test
  @DisplayName("The on-premise opt-out lets an internal host through, and skips the lookup")
  void given_optOut_should_notLookUpAnything() {
    // Arrange
    HostResolver failing =
        host -> {
          throw new AssertionError("must not be looked up");
        };

    // Act & Assert
    assertThat(EndpointUrls.refusal(true, failing, "http://10.0.0.5/")).isEmpty();
  }

  @Test
  @DisplayName("One internal answer is enough, whatever the other addresses of the host are")
  void given_mixedAnswers_should_beRefused() {
    // Arrange
    HostResolver mixed =
        host ->
            new InetAddress[] {
              InetAddress.getByAddress(new byte[] {8, 8, 8, 8}),
              InetAddress.getByAddress(new byte[] {10, 0, 0, 5})
            };

    // Act & Assert
    assertThat(EndpointUrls.refusal(false, mixed, "https://dual.example/"))
        .hasValueSatisfying(
            result -> assertThat(result.status()).isEqualTo(SECRET_STATUS.FORMAT_ERROR));
  }

  @Test
  @DisplayName("A lookup that is not answered in time is inconclusive, never a rejection")
  void given_stalledLookup_should_returnTimeout() {
    // Arrange — a resolver that outlasts its budget, as a hostile DNS server would.
    HostResolver stalled =
        new TimeoutHostResolver(
            host -> {
              try {
                Thread.sleep(5_000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return new InetAddress[0];
            },
            Duration.ofMillis(100),
            1);

    // Act & Assert
    assertThat(EndpointUrls.refusal(false, stalled, "https://slow.example/"))
        .hasValueSatisfying(result -> assertThat(result.status()).isEqualTo(SECRET_STATUS.TIMEOUT));
  }

  @Test
  @DisplayName("A host that does not exist is not mistaken for a stalled lookup")
  void given_unknownHost_should_beLetThrough() {
    // Arrange
    HostResolver unknown =
        host -> {
          throw new UnknownHostException(host);
        };

    // Act & Assert
    assertThat(EndpointUrls.refusal(false, unknown, "https://nowhere.example/")).isEmpty();
  }
}
