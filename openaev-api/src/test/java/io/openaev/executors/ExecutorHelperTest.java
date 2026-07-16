package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutorHelper tests")
class ExecutorHelperTest {

  private static final String INJECT_ID = "inject-1";
  private static final String AGENT_ID = "agent-1";
  private static final String TENANT_ID = "tenant-1";
  private static final String TOKEN = "secret-token-value";
  private static final String BASE_URL = "base-url-value";
  private static final String MAX_SIZE = "50";
  private static final String UNSECURED_CERTIFICATE = "true";
  private static final String WITH_PROXY = "false";

  @Test
  @DisplayName("Should replace inject, agent, tenant and token placeholders for Windows")
  void given_windowsCommandWithPlaceholders_should_replaceAllPlaceholders() {
    // Arrange
    String command =
        "run --inject=#{inject} --agent=#{agent} --tenant=#{tenant} --token=#{token} --loc=\"#{location}\" --baseUrl=#{baseUrl} --maxSize=#{maxSize} --unsecuredCertificate=#{unsecuredCertificate} --withProxy=#{withProxy}";

    // Act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Windows,
            command,
            INJECT_ID,
            AGENT_ID,
            TENANT_ID,
            TOKEN,
            BASE_URL,
            MAX_SIZE,
            UNSECURED_CERTIFICATE,
            WITH_PROXY);

    // Assert
    assertThat(result)
        .contains("--inject=" + INJECT_ID)
        .contains("--agent=" + AGENT_ID)
        .contains("--tenant=" + TENANT_ID)
        .contains("--token=" + TOKEN)
        .contains("--loc=" + ExecutorHelper.WINDOWS_LOCATION_PATH)
        .contains("--baseUrl=" + BASE_URL)
        .contains("--maxSize=" + MAX_SIZE)
        .contains("--unsecuredCertificate=" + UNSECURED_CERTIFICATE)
        .contains("--withProxy=" + WITH_PROXY)
        .doesNotContain("#{inject}")
        .doesNotContain("#{agent}")
        .doesNotContain("#{tenant}")
        .doesNotContain("#{token}")
        .doesNotContain("#{maxSize}")
        .doesNotContain("#{unsecuredCertificate}")
        .doesNotContain("#{withProxy}");
  }

  @Test
  @DisplayName("Should replace payload location placeholder for Linux platform")
  void shouldReplacePayloadLocationForLinux() {
    // prepare
    String command = "cat #{payload_location}/linpeas.sh";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN);

    // assert
    assertThat(result)
        .isEqualTo("cat " + ExecutorHelper.UNIX_PAYLOAD_LOCATION_PATH + "/linpeas.sh");
  }

  @Test
  @DisplayName("Should replace payload location placeholder for Windows platform")
  void shouldReplacePayloadLocationForWindows() {
    // prepare
    String command = "Get-Content #{payload_location}\\linpeas.ps1";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Windows, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN);

    // assert
    assertThat(result)
        .isEqualTo("Get-Content " + ExecutorHelper.WINDOWS_PAYLOAD_LOCATION_PATH + "\\linpeas.ps1");
  }

  @Test
  @DisplayName("Should replace location and payload location placeholders independently")
  void shouldReplaceLocationAndPayloadLocationIndependently() {
    // prepare
    String command = "cd \"#{location}\" && cat #{payload_location}/linpeas.sh";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN);

    // assert
    assertThat(result)
        .isEqualTo(
            "cd "
                + ExecutorHelper.UNIX_LOCATION_PATH
                + " && cat "
                + ExecutorHelper.UNIX_PAYLOAD_LOCATION_PATH
                + "/linpeas.sh");
  }

  @Test
  @DisplayName("Should replace an unquoted location placeholder")
  void given_unquotedLocation_should_replacePlaceholder() {
    // prepare
    String command = "cd #{location}";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN);

    // assert
    assertThat(result).isEqualTo("cd " + ExecutorHelper.UNIX_LOCATION_PATH);
  }

  @Test
  @DisplayName("Should replace token placeholder for Linux platform")
  void given_linuxCommandWithPlaceholders_should_replaceTokenTenantAndBaseUrl() {
    // Arrange
    String command =
        "echo #{token} #{tenant} #{baseUrl} #{maxSize} #{unsecuredCertificate} #{withProxy}";

    // Act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux,
            command,
            INJECT_ID,
            AGENT_ID,
            TENANT_ID,
            TOKEN,
            BASE_URL,
            MAX_SIZE,
            UNSECURED_CERTIFICATE,
            WITH_PROXY);

    // Assert
    assertThat(result)
        .isEqualTo(
            "echo "
                + TOKEN
                + " "
                + TENANT_ID
                + " "
                + BASE_URL
                + " "
                + MAX_SIZE
                + " "
                + UNSECURED_CERTIFICATE
                + " "
                + WITH_PROXY);
  }

  @Test
  @DisplayName("Should replace token placeholder for MacOS platform")
  void given_macosCommandWithTokenPlaceholder_should_replaceToken() {
    // Arrange
    String command = "curl -H \"Authorization: #{token}\" #{maxSize}";

    // Act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.MacOS,
            command,
            INJECT_ID,
            AGENT_ID,
            TENANT_ID,
            TOKEN,
            BASE_URL,
            MAX_SIZE,
            UNSECURED_CERTIFICATE,
            WITH_PROXY);

    // Assert
    assertThat(result).isEqualTo("curl -H \"Authorization: " + TOKEN + "\" " + MAX_SIZE);
  }

  @Test
  @DisplayName("Should replace token and payload location placeholders for MacOS platform")
  void shouldReplaceTokenForMacOS() {
    // prepare
    String command = "curl -H \"Authorization: #{token}\" #{payload_location}";

    // Act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.MacOS,
            command,
            INJECT_ID,
            AGENT_ID,
            TENANT_ID,
            TOKEN,
            BASE_URL,
            MAX_SIZE,
            UNSECURED_CERTIFICATE,
            WITH_PROXY);

    // assert
    assertThat(result)
        .isEqualTo(
            "curl -H \"Authorization: "
                + TOKEN
                + "\" "
                + ExecutorHelper.UNIX_PAYLOAD_LOCATION_PATH);
  }

  @Test
  @DisplayName("Should leave command unchanged when no placeholder is present")
  void given_commandWithoutPlaceholders_should_leaveCommandUnchanged() {
    // Arrange
    String command = "echo hello world";

    // Act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux,
            command,
            INJECT_ID,
            AGENT_ID,
            TENANT_ID,
            TOKEN,
            BASE_URL,
            MAX_SIZE,
            UNSECURED_CERTIFICATE,
            WITH_PROXY);

    // Assert
    assertThat(result).isEqualTo(command);
  }

  @Test
  @DisplayName("Should throw when platform is unsupported")
  void shouldThrowWhenPlatformIsUnsupported() {
    // act & assert
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Unknown, "echo hello", INJECT_ID, AGENT_ID, TENANT_ID, TOKEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported platform type: Unknown");
  }

  @Test
  @DisplayName(
      "Should propagate IllegalArgumentException from base overload when an argument is null")
  void given_nullCommand_should_throwIllegalArgumentException() {
    // Act & Assert
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux,
                    null,
                    INJECT_ID,
                    AGENT_ID,
                    TENANT_ID,
                    TOKEN,
                    BASE_URL,
                    MAX_SIZE,
                    UNSECURED_CERTIFICATE,
                    WITH_PROXY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName(
      "Should propagate IllegalArgumentException from base overload when base url argument is null")
  void given_nullBaseUrl_should_throwIllegalArgumentException() {
    // Act & Assert
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux,
                    "echo #{baseUrl}",
                    INJECT_ID,
                    AGENT_ID,
                    TENANT_ID,
                    TOKEN,
                    null,
                    MAX_SIZE,
                    UNSECURED_CERTIFICATE,
                    WITH_PROXY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux, "echo hello", null, AGENT_ID, TENANT_ID, TOKEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux, "echo hello", INJECT_ID, null, TENANT_ID, TOKEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux, "echo hello", INJECT_ID, AGENT_ID, null, TOKEN))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
