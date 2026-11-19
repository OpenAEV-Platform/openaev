package io.openaev.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.xtmhub.config.XtmHubConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Home API")
class HomeApiTest {

  private static final Pattern SCRIPT_NONCE = Pattern.compile("<script nonce=\"([^\"]*)\">");
  private static final Pattern HEADER_NONCE = Pattern.compile("script-src [^;]*'nonce-([^']*)'");

  private final XtmHubConfig xtmHubConfig = new XtmHubConfig();
  private final HomeApi homeApi = new HomeApi(xtmHubConfig);

  /** The index.html the frontend build starts from: the placeholders HomeApi fills are its own. */
  private String index;

  @BeforeEach
  void setUp() throws IOException {
    ReflectionTestUtils.setField(homeApi, "contextPath", "/openaev");
    xtmHubConfig.setUrl("https://hub.example.io");
    index =
        Files.readString(
            Path.of(System.getProperty("basedir", "."), "..", "openaev-front", "index.html"));
  }

  @Test
  @DisplayName("given the shell should authorize the inline script with the nonce of the header")
  void given_shell_should_authorizeInlineScriptWithTheNonceOfTheHeader() {
    // Act
    ResponseEntity<String> response = homeApi.shell(index);

    // Assert
    String policy = response.getHeaders().getFirst(SpaContentSecurityPolicy.HEADER);
    assertNotNull(policy);
    Matcher inScript = SCRIPT_NONCE.matcher(response.getBody());
    Matcher inHeader = HEADER_NONCE.matcher(policy);
    assertTrue(inScript.find(), "the inline script of index.html has no nonce");
    assertTrue(inHeader.find());
    assertEquals(inHeader.group(1), inScript.group(1));
    assertTrue(inScript.group(1).matches("[0-9a-f]{32}"));
  }

  @Test
  @DisplayName("given two requests should serve two different nonces")
  void given_twoRequests_should_serveDifferentNonces() {
    // Act
    ResponseEntity<String> first = homeApi.shell(index);
    ResponseEntity<String> second = homeApi.shell(index);

    // Assert
    assertNotEquals(
        first.getHeaders().getFirst(SpaContentSecurityPolicy.HEADER),
        second.getHeaders().getFirst(SpaContentSecurityPolicy.HEADER));
  }

  @Test
  @DisplayName("given the shell should report violations without enforcing the policy")
  void given_shell_should_reportOnly() {
    // Act
    ResponseEntity<String> response = homeApi.shell(index);

    // Assert
    assertNotNull(response.getHeaders().getFirst("Content-Security-Policy-Report-Only"));
    assertNull(response.getHeaders().getFirst("Content-Security-Policy"));
  }

  @Test
  @DisplayName("given the shell should let the browser reach the configured XTM Hub")
  void given_shell_should_letTheBrowserReachTheConfiguredXtmHub() {
    // Act
    ResponseEntity<String> response = homeApi.shell(index);

    // Assert
    String policy = response.getHeaders().getFirst(SpaContentSecurityPolicy.HEADER);
    assertTrue(policy.contains("connect-src 'self' https://hub.example.io;"), policy);
  }

  @Test
  @DisplayName("given the shell should fill every placeholder and stay uncached")
  void given_shell_should_fillEveryPlaceholderAndStayUncached() {
    // Act
    ResponseEntity<String> response = homeApi.shell(index);

    // Assert
    assertTrue(response.getBody().contains("window.BASE_PATH = \"/openaev\";"));
    assertFalse(response.getBody().contains("%"), "a placeholder is left in the shell");
    assertEquals("no-cache", response.getHeaders().getCacheControl());
  }
}
