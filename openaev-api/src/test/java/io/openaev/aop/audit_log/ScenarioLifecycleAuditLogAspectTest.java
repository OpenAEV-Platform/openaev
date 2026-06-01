package io.openaev.aop.audit_log;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Action;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.service.LogService;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class ScenarioLifecycleAuditLogAspectTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @MockitoSpyBean private AuditLogger auditLogger;

  @MockitoSpyBean private LogService logService;

  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  @BeforeEach
  void setup() {
    reset(auditLogger, logService, auditLogTransportDispatcherUtils);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(auditLogger).isAuditLoggingValid(any(Action.class));
    doReturn(true).when(logService).isEnabled();
  }

  @Nested
  @DisplayName("Scenario lifecycle mutation events")
  class ScenarioLifecycleMutationEvents {

    @Test
    @WithMockUser(isAdmin = true)
    void given_scenarioLifecycleActions_should_logChildCreateAndStatusChangeEvents()
        throws Exception {
      // Arrange
      String scenarioName = "audit-scenario-" + System.currentTimeMillis();
      String teamName = "audit-team-" + System.currentTimeMillis();

      String createScenarioResponse =
          mvc.perform(
                  post(SCENARIO_URI)
                      .content(
                          asJsonString(
                              Map.ofEntries(
                                  Map.entry("scenario_name", scenarioName),
                                  Map.entry("scenario_category", "attack-scenario"),
                                  Map.entry("scenario_main_focus", "incident-response"),
                                  Map.entry("scenario_severity", "high"),
                                  Map.entry("scenario_subtitle", ""),
                                  Map.entry("scenario_description", ""),
                                  Map.entry("scenario_tags", List.of()),
                                  Map.entry("scenario_external_reference", ""),
                                  Map.entry("scenario_external_url", ""),
                                  Map.entry("scenario_mail_from", "openaev-dev@test.io"),
                                  Map.entry(
                                      "scenario_mails_reply_to", List.of("openaev-dev@test.io")),
                                  Map.entry("scenario_message_header", "SIMULATION HEADER"),
                                  Map.entry("scenario_message_footer", "SIMULATION FOOTER"))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createScenarioResponse, "$.scenario_id");

      String createInjectResponse =
          mvc.perform(
                  post(SCENARIO_URI + "/{scenarioId}/injects", scenarioId)
                      .content(
                          asJsonString(
                              Map.of(
                                  "inject_title",
                                  "audit-inject-" + System.currentTimeMillis(),
                                  "inject_injector_contract",
                                  EMAIL_DEFAULT,
                                  "inject_depends_duration",
                                  0)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String injectId = JsonPath.read(createInjectResponse, "$.inject_id");

      String createTeamResponse =
          mvc.perform(
                  post(TEAM_URI)
                      .content(
                          asJsonString(
                              Map.of(
                                  "team_name", teamName,
                                  "team_tags", List.of(),
                                  "team_exercises", List.of(),
                                  "team_scenarios", List.of(),
                                  "team_contextual", false)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createTeamResponse, "$.team_id");

      // Act
      mvc.perform(
              put(SCENARIO_URI + "/{scenarioId}/teams/replace", scenarioId)
                  .content(asJsonString(Map.of("scenario_teams", List.of(teamId))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      mvc.perform(post(SCENARIO_URI + "/{scenarioId}/exercise/running", scenarioId).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
      verify(auditLogTransportDispatcherUtils, timeout(3000).atLeast(4))
          .dispatch(eventCaptor.capture(), any());

      List<LogEvent> lifecycleEvents =
          eventCaptor.getAllValues().stream()
              .filter(Objects::nonNull)
              .filter(event -> isScenarioLifecycleEvent(event, scenarioId))
              .toList();

      assertThat(lifecycleEvents).isNotEmpty();
      assertThat(lifecycleEvents)
          .allSatisfy(event -> assertThat(event.getEventType()).isEqualTo("mutation"));

      LogEvent injectLifecycleEvent =
          lifecycleEvents.stream()
              .filter(event -> requestUrlContains(event, "/scenarios/" + scenarioId + "/injects"))
              .filter(event -> hasInjectOutput(event, injectId, scenarioId))
              .findFirst()
              .orElse(null);

      assertThat(injectLifecycleEvent).isNotNull();
      assertThat(injectLifecycleEvent.getEventType()).isEqualTo("mutation");

      LogEvent teamAssociationLifecycleEvent =
          lifecycleEvents.stream()
              .filter(
                  event -> requestUrlContains(event, "/scenarios/" + scenarioId + "/teams/replace"))
              .filter(event -> hasTeamAssociationOutput(event, teamId, scenarioId))
              .findFirst()
              .orElse(null);

      assertThat(teamAssociationLifecycleEvent).isNotNull();
      assertThat(teamAssociationLifecycleEvent.getEventType()).isEqualTo("mutation");

      LogEvent launchStatusChangeEvent =
          lifecycleEvents.stream()
              .filter(event -> "status_change".equals(event.getEventScope()))
              .findFirst()
              .orElse(null);

      assertThat(launchStatusChangeEvent).isNotNull();
      assertThat(launchStatusChangeEvent.getEventType()).isEqualTo("mutation");
    }
  }

  private static boolean isScenarioLifecycleEvent(LogEvent event, String scenarioId) {
    if (event.getRequestMetadata() != null
        && event.getRequestMetadata().getUrl() != null
        && event.getRequestMetadata().getUrl().contains("/scenarios/" + scenarioId)) {
      return true;
    }

    return scenarioId.equals(contextValue(event, "resource_id"))
        || scenarioId.equals(contextValue(event, "parent_id"))
        || scenarioId.equals(contextValue(event, "scenario_id"));
  }

  private static String contextValue(LogEvent event, String key) {
    if (event.getContextData() == null) {
      return null;
    }

    Object value = event.getContextData().get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static boolean requestUrlContains(LogEvent event, String pathFragment) {
    return event.getRequestMetadata() != null
        && event.getRequestMetadata().getUrl() != null
        && event.getRequestMetadata().getUrl().contains(pathFragment);
  }

  @SuppressWarnings("unchecked")
  private static boolean hasInjectOutput(LogEvent event, String injectId, String scenarioId) {
    if (event.getContextData() == null) {
      return false;
    }

    Object output = event.getContextData().get("output");
    if (!(output instanceof Map<?, ?> outputMap)) {
      return false;
    }

    return injectId.equals(String.valueOf(outputMap.get("inject_id")))
        && scenarioId.equals(String.valueOf(outputMap.get("inject_scenario")));
  }

  @SuppressWarnings("unchecked")
  private static boolean hasTeamAssociationOutput(
      LogEvent event, String teamId, String scenarioId) {
    if (event.getContextData() == null) {
      return false;
    }

    Object output = event.getContextData().get("output");
    if (!(output instanceof Collection<?> outputCollection)) {
      return false;
    }

    return outputCollection.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .anyMatch(
            item -> {
              Object outputTeamId = item.get("team_id");
              Object outputScenarioIds = item.get("team_scenarios");
              if (!(outputScenarioIds instanceof Collection<?> scenarioIds)) {
                return false;
              }
              return teamId.equals(String.valueOf(outputTeamId))
                  && scenarioIds.stream().map(String::valueOf).anyMatch(scenarioId::equals);
            });
  }
}
