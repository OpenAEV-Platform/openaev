package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.IMPLANT_BASE_NAME;
import static io.openaev.executors.sentinelone.service.SentinelOneGarbageCollectorService.UNIX_CLEAN_IMPLANTS_COMMAND;
import static io.openaev.executors.sentinelone.service.SentinelOneGarbageCollectorService.WINDOWS_CLEAN_IMPLANTS_COMMAND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAction;
import io.openaev.service.AgentService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("SentinelOne garbage collector")
public class SentinelOneGarbageCollectorServiceTest {

  private static final String EXECUTOR_ID = "test-executor-id";

  @Mock private AgentService agentService;
  @Mock private SentinelOneExecutorContextService sentinelOneExecutorContextService;
  @Mock SentinelOneExecutorConfig config;

  private SentinelOneGarbageCollectorService sentinelOneGarbageCollectorService;

  @BeforeEach
  void setUp() {
    sentinelOneGarbageCollectorService =
        new SentinelOneGarbageCollectorService(
            config, sentinelOneExecutorContextService, agentService, EXECUTOR_ID);
  }

  @Nested
  @DisplayName("When running the collector")
  class WhenRunningTheCollector {

    @Test
    @DisplayName("Given a Windows agent, should send the UTF-16LE clean command")
    void given_windowsAgent_should_sendUtf16CleanCommand() {
      // -- ARRANGE --
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(EndpointFixture.createEndpoint());
      when(agentService.getAgentsByExecutorId(EXECUTOR_ID)).thenReturn(List.of(agent));
      when(config.getWindowsScriptId()).thenReturn("test script");

      // -- ACT --
      sentinelOneGarbageCollectorService.run();

      // -- ASSERT --
      ArgumentCaptor<List<SentinelOneAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
      verify(sentinelOneExecutorContextService).executeActions(actionsCaptor.capture());
      assertEquals(1, actionsCaptor.getValue().size());
      SentinelOneAction action = actionsCaptor.getValue().get(0);
      assertEquals("test script", action.getScriptId());
      assertEquals(agent.getExternalReference(), action.getAgentExternalReference());
      assertEquals(
          WINDOWS_CLEAN_IMPLANTS_COMMAND,
          new String(
              Base64.getDecoder().decode(action.getCommandEncoded()), StandardCharsets.UTF_16LE));
    }

    @Test
    @DisplayName("Given a Linux agent, should send the UTF-8 clean command")
    void given_linuxAgent_should_sendUtf8CleanCommand() {
      // -- ARRANGE --
      Agent agent =
          AgentFixture.createAgent(
              EndpointFixture.createEndpointWithPlatform("linux-ep", Endpoint.PLATFORM_TYPE.Linux),
              "12345");
      when(agentService.getAgentsByExecutorId(EXECUTOR_ID)).thenReturn(List.of(agent));
      when(config.getUnixScriptId()).thenReturn("unix script");

      // -- ACT --
      sentinelOneGarbageCollectorService.run();

      // -- ASSERT --
      ArgumentCaptor<List<SentinelOneAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
      verify(sentinelOneExecutorContextService).executeActions(actionsCaptor.capture());
      assertEquals(1, actionsCaptor.getValue().size());
      SentinelOneAction action = actionsCaptor.getValue().get(0);
      assertEquals("unix script", action.getScriptId());
      assertEquals(
          UNIX_CLEAN_IMPLANTS_COMMAND,
          new String(
              Base64.getDecoder().decode(action.getCommandEncoded()), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Given no agent, should not call the SentinelOne API")
    void given_noAgent_should_notCallApi() {
      // -- ARRANGE --
      when(agentService.getAgentsByExecutorId(EXECUTOR_ID)).thenReturn(List.of());

      // -- ACT --
      sentinelOneGarbageCollectorService.run();

      // -- ASSERT --
      verify(sentinelOneExecutorContextService, never()).executeActions(anyList());
    }
  }

  @Nested
  @DisplayName("Clean commands safety")
  class CleanCommandsSafety {

    @Test
    @DisplayName("Windows command only targets implant directories and stays silent")
    void windowsCommand_should_onlyTargetImplantDirectories() {
      // -- ASSERT --
      assertThat(WINDOWS_CLEAN_IMPLANTS_COMMAND)
          .contains("-Filter \"" + IMPLANT_BASE_NAME + "*\"")
          .contains("-ErrorAction SilentlyContinue")
          .doesNotContain("-Directory -Recurse");
    }

    @Test
    @DisplayName("Unix command does not descend into removed directories and never fails")
    void unixCommand_should_notDescendIntoRemovedDirectories() {
      // -- ASSERT --
      assertThat(UNIX_CLEAN_IMPLANTS_COMMAND)
          .contains("-mindepth 1 -maxdepth 1")
          .contains("-name '" + IMPLANT_BASE_NAME + "*'")
          .endsWith("|| true");
    }
  }

  @Nested
  @DisplayName("Default schedule")
  class DefaultSchedule {

    /** Walks a full year: a step expression collapses to a 24h gap at month boundaries. */
    @Test
    @DisplayName("Default cron is parseable and fires exactly once a day at 3:00 AM")
    void defaultCron_should_fireDailyAtThreeAm() {
      // -- ARRANGE --
      CronTrigger trigger = new CronTrigger(SentinelOneExecutorConfig.DEFAULT_CLEAN_IMPLANT_CRON);
      SimpleTriggerContext context =
          new SimpleTriggerContext(
              Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

      // -- ACT / ASSERT --
      Instant previous = null;
      for (int i = 0; i < 365; i++) {
        Instant next = trigger.nextExecution(context);
        assertThat(next).isNotNull();
        assertThat(next.atZone(ZoneOffset.UTC).getHour()).isEqualTo(3);
        if (previous != null) {
          assertThat(Duration.between(previous, next)).isEqualTo(Duration.ofDays(1));
        }
        previous = next;
        context.update(next, next, next);
      }
    }
  }
}
