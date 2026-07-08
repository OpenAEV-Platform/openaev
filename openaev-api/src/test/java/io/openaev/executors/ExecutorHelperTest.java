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

  @Test
  @DisplayName("Should replace inject, agent, tenant and token placeholders for Windows")
  void shouldReplaceAllPlaceholdersIncludingTokenForWindows() {
    // prepare
    String command =
        "run --inject=#{inject} --agent=#{agent} --tenant=#{tenant} --token=#{token} --loc=\"#{location}\"";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Windows, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN, BASE_URL);

    // assert
    assertThat(result)
        .contains("--inject=" + INJECT_ID)
        .contains("--agent=" + AGENT_ID)
        .contains("--tenant=" + TENANT_ID)
        .contains("--token=" + TOKEN)
        .contains("--loc=" + ExecutorHelper.WINDOWS_LOCATION_PATH)
        .doesNotContain("#{inject}")
        .doesNotContain("#{agent}")
        .doesNotContain("#{tenant}")
        .doesNotContain("#{token}");
  }

  @Test
  @DisplayName("Should replace token placeholder for Linux platform")
  void shouldReplaceTokenForLinux() {
    // prepare
    String command = "echo #{token} #{tenant}";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN, BASE_URL);

    // assert
    assertThat(result).isEqualTo("echo " + TOKEN + " " + TENANT_ID);
  }

  @Test
  @DisplayName("Should replace token placeholder for MacOS platform")
  void shouldReplaceTokenForMacOS() {
    // prepare
    String command = "curl -H \"Authorization: #{token}\"";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.MacOS, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN, BASE_URL);

    // assert
    assertThat(result).isEqualTo("curl -H \"Authorization: " + TOKEN + "\"");
  }

  @Test
  @DisplayName("Should leave command unchanged when no placeholder is present")
  void shouldLeaveCommandUnchangedWhenNoPlaceholder() {
    // prepare
    String command = "echo hello world";

    // act
    String result =
        ExecutorHelper.replaceArgs(
            PLATFORM_TYPE.Linux, command, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN, BASE_URL);

    // assert
    assertThat(result).isEqualTo(command);
  }

  @Test
  @DisplayName(
      "Should propagate IllegalArgumentException from base overload when an argument is null")
  void shouldThrowWhenAnyArgumentIsNull() {
    // act & assert
    assertThatThrownBy(
            () ->
                ExecutorHelper.replaceArgs(
                    PLATFORM_TYPE.Linux, null, INJECT_ID, AGENT_ID, TENANT_ID, TOKEN, BASE_URL))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
