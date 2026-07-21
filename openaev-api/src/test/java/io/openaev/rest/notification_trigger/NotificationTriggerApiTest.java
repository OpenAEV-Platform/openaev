package io.openaev.rest.notification_trigger;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.rest.notification_trigger.form.NotificationTriggerInput;
import io.openaev.service.notification.NotifierService;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
public class NotificationTriggerApiTest extends IntegrationTest {

  public static final String NOTIFICATION_TRIGGER_URI = "/api/notification-triggers";

  @Autowired private MockMvc mvc;
  @Autowired private NotificationTriggerRepository notificationTriggerRepository;
  @Autowired private NotifierService notifierService;

  @AfterEach
  void afterEach() {
    notificationTriggerRepository.deleteAll();
  }

  private String uiNotifierId() {
    notifierService.ensureBuiltInNotifiers(TenantContext.getCurrentTenant());
    return notifierService.findAll().stream()
        .filter(notifier -> notifier.getType() == io.openaev.database.model.NotifierType.UI)
        .findFirst()
        .orElseThrow()
        .getId();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A live trigger can be created, updated and deleted")
  void liveTriggerLifecycle() throws Exception {
    NotificationTriggerInput input =
        NotificationTriggerInput.builder()
            .name("Scenario watcher")
            .type(NotificationTriggerType.LIVE)
            .resourceType(ResourceType.SCENARIO)
            .eventTypes(
                List.of(NotificationTriggerEventType.CREATE, NotificationTriggerEventType.UPDATE))
            .notifierIds(List.of(uiNotifierId()))
            .build();

    String created =
        mvc.perform(
                post(NOTIFICATION_TRIGGER_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String triggerId = JsonPath.read(created, "$.notification_trigger_id");
    assertEquals("Scenario watcher", JsonPath.read(created, "$.notification_trigger_name"));
    assertEquals("LIVE", JsonPath.read(created, "$.notification_trigger_type"));
    assertEquals("SCENARIO", JsonPath.read(created, "$.notification_trigger_resource_type"));
    assertEquals(
        testUserHolder.get().getId(), JsonPath.read(created, "$.notification_trigger_owner"));

    NotificationTriggerInput update =
        NotificationTriggerInput.builder()
            .name("Scenario watcher updated")
            .type(NotificationTriggerType.LIVE)
            .resourceType(ResourceType.SCENARIO)
            .eventTypes(List.of(NotificationTriggerEventType.DELETE))
            .notifierIds(List.of(uiNotifierId()))
            .build();
    String updated =
        mvc.perform(
                put(NOTIFICATION_TRIGGER_URI + "/" + triggerId)
                    .content(asJsonString(update))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals("Scenario watcher updated", JsonPath.read(updated, "$.notification_trigger_name"));

    mvc.perform(delete(NOTIFICATION_TRIGGER_URI + "/" + triggerId).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertFalse(notificationTriggerRepository.findById(triggerId).isPresent());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A live trigger without event types is rejected")
  void liveTriggerRequiresEventTypes() throws Exception {
    NotificationTriggerInput input =
        NotificationTriggerInput.builder()
            .name("Broken trigger")
            .type(NotificationTriggerType.LIVE)
            .resourceType(ResourceType.SCENARIO)
            .eventTypes(List.of())
            .notifierIds(List.of(uiNotifierId()))
            .build();

    mvc.perform(
            post(NOTIFICATION_TRIGGER_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("A digest trigger requires composed triggers and a valid trigger time")
  void digestTriggerValidation() throws Exception {
    // No composed triggers -> rejected
    NotificationTriggerInput withoutChildren =
        NotificationTriggerInput.builder()
            .name("Empty digest")
            .type(NotificationTriggerType.DIGEST)
            .period(NotificationTriggerPeriod.DAY)
            .triggerTime("09:00")
            .notifierIds(List.of(uiNotifierId()))
            .build();
    mvc.perform(
            post(NOTIFICATION_TRIGGER_URI)
                .content(asJsonString(withoutChildren))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    // Create a live trigger to compose
    NotificationTriggerInput live =
        NotificationTriggerInput.builder()
            .name("Composable live")
            .type(NotificationTriggerType.LIVE)
            .resourceType(ResourceType.SCENARIO)
            .eventTypes(List.of(NotificationTriggerEventType.CREATE))
            .notifierIds(List.of(uiNotifierId()))
            .build();
    String liveResponse =
        mvc.perform(
                post(NOTIFICATION_TRIGGER_URI)
                    .content(asJsonString(live))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String liveId = JsonPath.read(liveResponse, "$.notification_trigger_id");

    // Invalid trigger time -> rejected
    NotificationTriggerInput invalidTime =
        NotificationTriggerInput.builder()
            .name("Bad time digest")
            .type(NotificationTriggerType.DIGEST)
            .period(NotificationTriggerPeriod.WEEK)
            .triggerTime("09:00") // missing the day-of-week prefix
            .childTriggerIds(List.of(liveId))
            .notifierIds(List.of(uiNotifierId()))
            .build();
    mvc.perform(
            post(NOTIFICATION_TRIGGER_URI)
                .content(asJsonString(invalidTime))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    // Valid digest -> created
    NotificationTriggerInput valid =
        NotificationTriggerInput.builder()
            .name("Weekly digest")
            .type(NotificationTriggerType.DIGEST)
            .period(NotificationTriggerPeriod.WEEK)
            .triggerTime("1-09:00")
            .childTriggerIds(List.of(liveId))
            .notifierIds(List.of(uiNotifierId()))
            .build();
    String digestResponse =
        mvc.perform(
                post(NOTIFICATION_TRIGGER_URI)
                    .content(asJsonString(valid))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals("DIGEST", JsonPath.read(digestResponse, "$.notification_trigger_type"));
    assertEquals(List.of(liveId), JsonPath.read(digestResponse, "$.notification_trigger_children"));
  }

  @Test
  @WithMockUser
  @DisplayName("A non-admin user cannot target other recipients")
  void nonAdminCannotTargetOthers() throws Exception {
    NotificationTriggerInput input =
        NotificationTriggerInput.builder()
            .name("Targeting trigger")
            .type(NotificationTriggerType.LIVE)
            .resourceType(ResourceType.SCENARIO)
            .eventTypes(List.of(NotificationTriggerEventType.CREATE))
            .notifierIds(List.of(uiNotifierId()))
            .recipientUserIds(List.of("00000000-0000-0000-0000-000000000000"))
            .build();

    mvc.perform(
            post(NOTIFICATION_TRIGGER_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
  }
}
