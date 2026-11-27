package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AgentUtils Tests")
class AgentUtilsTest {

  private static Agent buildAgent(String id) {
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setId(id);
    return agent;
  }

  private static Inject buildInjectWithAgentTrace(Agent agent, ExecutionTraceStatus status) {
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    injectStatus.addTrace(
        new ExecutionTrace(
            injectStatus, status, List.of(), "test", ExecutionTraceAction.COMPLETE, agent, null));
    Inject inject = InjectFixture.getDefaultInject();
    inject.setStatus(injectStatus);
    return inject;
  }

  @Nested
  @DisplayName("Method: hasOnlyValidTraces")
  class HasOnlyValidTraces {

    @Test
    @DisplayName("Given a not targeted trace on the agent should return false")
    void given_clearanceRestrictedTraceOnAgent_should_returnFalse() {
      // -- ARRANGE --
      Agent agentRed = buildAgent("agent-red");
      Inject inject =
          buildInjectWithAgentTrace(agentRed, ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED);

      // -- ACT --
      boolean result = AgentUtils.hasOnlyValidTraces(inject, agentRed);

      // -- ASSERT --
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Given a not targeted trace on another agent should return true")
    void given_clearanceRestrictedTraceOnOtherAgent_should_returnTrue() {
      // -- ARRANGE --
      Agent agentRed = buildAgent("agent-red");
      Agent agentGreen = buildAgent("agent-green");
      Inject inject =
          buildInjectWithAgentTrace(agentRed, ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED);

      // -- ACT --
      boolean result = AgentUtils.hasOnlyValidTraces(inject, agentGreen);

      // -- ASSERT --
      assertThat(result).isTrue();
    }
  }

  @Nested
  @DisplayName("Values substituted into agent scripts")
  class AgentScriptInputValidation {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "/opt/openaev-agent",
          "/opt/openaev-agent/v1.2",
          ".local/openaev-agent-session",
          "/home/user/.local/openaev-agent-service"
        })
    @DisplayName("Given a plain Unix installation directory should accept it")
    void given_plainUnixDir_should_accept(String dir) {
      assertThat(AgentUtils.isSafeInstallationDir("linux", dir)).isTrue();
      assertThat(AgentUtils.isSafeInstallationDir("macos", dir)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "C:\\Program Files (x86)\\Filigran\\OAEV Agent",
          "C:\\Users\\john\\OAEVAgent-Session-john",
          ".openaev",
          "D:\\openaev"
        })
    @DisplayName("Given a plain Windows installation directory should accept it")
    void given_plainWindowsDir_should_accept(String dir) {
      assertThat(AgentUtils.isSafeInstallationDir("Windows", dir)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "/tmp/openaev\"; echo unsafe; #",
          "/tmp/openaev\"\necho unsafe",
          "/opt/openaev$(id)",
          "/opt/openaev`id`",
          "/opt/${HOME}/openaev",
          "/opt/openaev;id",
          "/opt/openaev|id",
          "/opt/openaev&id",
          "/opt/openaev'x",
          "/opt/openaev\n",
          "/opt/openaev\r",
          "/opt/openaev\\",
          "/opt/openaev\\x",
          "/opt/openaev ",
          "/etc/passwd x/openaev",
          "/opt/../etc/openaev",
          "/opt/./openaev",
          "/opt/openaev/..",
          "-openaev",
          "/opt/openaev/-rf",
          "/etc",
          "/",
          "",
          "/opt/openaevĄ",
          "/opt/openaevē",
        })
    @DisplayName("Given a Unix installation directory able to alter the script should reject it")
    void given_unsafeUnixDir_should_reject(String dir) {
      assertThat(AgentUtils.isSafeInstallationDir("linux", dir)).isFalse();
      assertThat(AgentUtils.isSafeInstallationDir("macos", dir)).isFalse();
      assertThatThrownBy(() -> AgentUtils.requireSafeInstallationDir("linux", dir))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "C:\\x\"; echo unsafe; #",
          "C:\\OAEV\\",
          "C:\\OAEV\\x\\",
          "C:\\OAEV\\\\x",
          "\\\\attacker.example\\share\\oaev",
          "HKLM:\\oaev",
          "Env:\\oaev",
          "C:\\Windows",
          "C:\\OAEV\\..\\Windows",
          "C:\\OAEV\\.",
          "C:\\OAEV $(id)",
          "C:\\OAEV`id`",
          "C:\\OAEV;id",
          "C:\\OAEV'x",
          "C:\\OAEV&x",
          "C:\\ OAEV",
          "C:\\OAEV \\x",
          "C:\\OAEVē",
          "C:\\OAEV”x",
          "C:/OAEV/x",
          "$HOME\\.openaev"
        })
    @DisplayName("Given a Windows installation directory able to alter the script should reject it")
    void given_unsafeWindowsDir_should_reject(String dir) {
      assertThat(AgentUtils.isSafeInstallationDir("Windows", dir)).isFalse();
      assertThatThrownBy(() -> AgentUtils.requireSafeInstallationDir("Windows", dir))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given a null or oversized installation directory should reject it")
    void given_nullOrOversizedInstallationDir_should_reject() {
      assertThat(AgentUtils.isSafeInstallationDir("linux", null)).isFalse();
      assertThat(AgentUtils.isSafeInstallationDir("linux", "/openaev/" + "a".repeat(250)))
          .isFalse();
      assertThat(AgentUtils.isSafeInstallationDir("Windows", "C:\\OAEV\\" + "a".repeat(250)))
          .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "openaev-agent",
          "openaev-agent-session",
          "OAEVAgentService",
          "OAEVAgent-Service",
          "OAEVAgent-Session",
          "openbas-agent"
        })
    @DisplayName("Given a service name of the agent family should accept it")
    void given_agentServiceName_should_accept(String name) {
      assertThat(AgentUtils.isSafeServiceName(name)).isTrue();
      assertThat(AgentUtils.requireSafeServiceName(name)).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "sshd",
          "auditd",
          "openaev\"; echo unsafe; #",
          "openaev$(id)",
          "openaev`id`",
          "openaev;id",
          "openaev id",
          "openaev\nid",
          "openaevē",
          "-openaev",
          ""
        })
    @DisplayName("Given a service name outside the agent family or able to alter the script")
    void given_unsafeServiceName_should_reject(String name) {
      assertThat(AgentUtils.isSafeServiceName(name)).isFalse();
      assertThatThrownBy(() -> AgentUtils.requireSafeServiceName(name))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given a service name longer than 128 characters should reject it")
    void given_oversizedServiceName_should_reject() {
      assertThat(AgentUtils.isSafeServiceName("openaev" + "a".repeat(122))).isFalse();
      assertThat(AgentUtils.isSafeServiceName("openaev" + "a".repeat(121))).isTrue();
    }

    @Test
    @DisplayName("Given absent or empty values the server default applies and nothing is rejected")
    void given_absentValues_should_beSafe() {
      assertThat(AgentUtils.areSafeAgentScriptInputs("linux", null, null)).isTrue();
      assertThat(AgentUtils.areSafeAgentScriptInputs("Windows", "", "")).isTrue();
    }

    @Test
    @DisplayName("Given one unsafe value among two should reject the pair")
    void given_oneUnsafeValue_should_rejectPair() {
      assertThat(
              AgentUtils.areSafeAgentScriptInputs("linux", "/opt/openaev-agent", "openaev-agent"))
          .isTrue();
      assertThat(AgentUtils.areSafeAgentScriptInputs("linux", "/opt/openaev-agent", "sshd"))
          .isFalse();
      assertThat(AgentUtils.areSafeAgentScriptInputs("linux", "/etc/x\ny", "openaev-agent"))
          .isFalse();
    }
  }
}
