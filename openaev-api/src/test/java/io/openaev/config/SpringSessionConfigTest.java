package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spring session cookie serialization")
class SpringSessionConfigTest {

  @Mock private OpenAEVConfig openAEVConfig;

  private String writeCookie(String configuredSameSite) {
    SpringSessionConfig config = new SpringSessionConfig(openAEVConfig);
    // Persistent-cookie branch is irrelevant here; keep it a browser-session cookie.
    when(openAEVConfig.isSessionCookie()).thenReturn(true);
    ReflectionTestUtils.setField(config, "sessionTimeout", java.time.Duration.ofMinutes(1440));
    ReflectionTestUtils.setField(config, "sessionCookieSameSite", configuredSameSite);
    CookieSerializer serializer = config.cookieSerializer();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSecure(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    serializer.writeCookieValue(
        new CookieSerializer.CookieValue(request, response, "the-session-id"));
    return response.getHeader("Set-Cookie");
  }

  @Nested
  @DisplayName("SameSite handling (SSO must not be broken by an over-restrictive attribute)")
  class SameSiteHandling {

    @Test
    @DisplayName("given blank config should omit the SameSite attribute")
    void given_blankConfig_should_omitSameSite() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act
      String setCookie = writeCookie("");

      // Assert - the pre-Spring-Session behavior: no SameSite attribute at all.
      assertFalse(setCookie.contains("SameSite"), setCookie);
    }

    @Test
    @DisplayName("given a null config should omit the SameSite attribute")
    void given_nullConfig_should_omitSameSite() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act
      String setCookie = writeCookie(null);

      // Assert
      assertFalse(setCookie.contains("SameSite"), setCookie);
    }

    @Test
    @DisplayName("given None should set SameSite=None and force Secure")
    void given_none_should_setSameSiteNoneAndSecure() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act
      String setCookie = writeCookie("None");

      // Assert
      assertTrue(setCookie.contains("SameSite=None"), setCookie);
      assertTrue(setCookie.contains("Secure"), setCookie);
    }

    @Test
    @DisplayName("given Lax should set SameSite=Lax without forcing Secure")
    void given_lax_should_setSameSiteLax() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act
      String setCookie = writeCookie("Lax");

      // Assert
      assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
      assertFalse(setCookie.contains("Secure"), setCookie);
    }

    @Test
    @DisplayName(
        "given Strict (any case, padded) should set SameSite=Strict without forcing Secure")
    void given_strict_should_setSameSiteStrict() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act - mixed case and surrounding whitespace must still normalize.
      String setCookie = writeCookie("  sTrIcT  ");

      // Assert
      assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
      assertFalse(setCookie.contains("Secure"), setCookie);
    }

    @Test
    @DisplayName("given an unknown value should omit the SameSite attribute rather than guess")
    void given_unknownValue_should_omitSameSite() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(false);

      // Act
      String setCookie = writeCookie("banana");

      // Assert
      assertFalse(setCookie.contains("SameSite"), setCookie);
    }

    @Test
    @DisplayName("given cookie-secure true should keep Secure even when SameSite is omitted")
    void given_cookieSecure_should_keepSecure() {
      // Arrange
      when(openAEVConfig.isCookieSecure()).thenReturn(true);

      // Act
      String setCookie = writeCookie("");

      // Assert
      assertTrue(setCookie.contains("Secure"), setCookie);
      assertFalse(setCookie.contains("SameSite"), setCookie);
    }
  }
}
