package io.openaev.rest.notification_rule;

import static io.openaev.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.NotificationRuleRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.notification_rule.form.CreateNotificationRuleInput;
import io.openaev.rest.notification_rule.form.UpdateNotificationRuleInput;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestInstance(PER_CLASS)
public class NotificationRuleApiTest extends IntegrationTest {

  public static final String NOTIFICATION_RULE_URI = "/api/notification-rules";

  @Autowired private MockMvc mvc;

  @Autowired private NotificationRuleRepository notificationRuleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ScenarioRepository scenarioRepository;

  @AfterEach
  void afterEach() {
    notificationRuleRepository.deleteAll();
    scenarioRepository.deleteAll();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void createNotificationRule() throws Exception {
    User testUser = testUserHolder.get();

    Scenario scenario = new Scenario();
    scenario.setDescription("Test scenario");
    scenario.setName("Test scenario");
    scenario.setFrom("test@openaev.io");
    scenario = scenarioRepository.save(scenario);

    CreateNotificationRuleInput input =
        CreateNotificationRuleInput.builder()
            .trigger("DIFFERENCE")
            .type("EMAIL")
            .resourceType("SCENARIO")
            .resourceId(scenario.getId())
            .subject("testsubject")
            .build();
    String response =
        mvc.perform(
                post(NOTIFICATION_RULE_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(input.getSubject(), JsonPath.read(response, "$.notification_rule_subject"));
    assertEquals(input.getTrigger(), JsonPath.read(response, "$.notification_rule_trigger"));
    assertEquals(input.getResourceId(), JsonPath.read(response, "$.notification_rule_resource_id"));
    assertEquals(
        input.getResourceType(), JsonPath.read(response, "$.notification_rule_resource_type"));
    assertEquals(testUser.getId(), JsonPath.read(response, "$.notification_rule_owner"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void createNotificationRule_unexisting_scenario_id() throws Exception {

    CreateNotificationRuleInput input =
        CreateNotificationRuleInput.builder()
            .trigger("DIFFERENCE")
            .type("EMAIL")
            .resourceType("SCENARIO")
            .resourceId("testid")
            .subject("testsubject")
            .build();
    String response =
        mvc.perform(
                post(NOTIFICATION_RULE_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void updateNotificationRule() throws Exception {
    NotificationRule notificationRule =
        createNotificationRuleInDb("resourceid", testUserHolder.get());

    String updatedSubject = "updated Subject";
    UpdateNotificationRuleInput updatedInput =
        UpdateNotificationRuleInput.builder().subject(updatedSubject).build();
    String response =
        mvc.perform(
                put(NOTIFICATION_RULE_URI + "/" + notificationRule.getId())
                    .content(asJsonString(updatedInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(updatedSubject, JsonPath.read(response, "$.notification_rule_subject"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void updateNotificationRule_WITH_unexisting_id() throws Exception {

    String updatedSubject = "updated Subject";
    UpdateNotificationRuleInput updatedInput =
        UpdateNotificationRuleInput.builder().subject(updatedSubject).build();
    mvc.perform(
            put(NOTIFICATION_RULE_URI + "/randomid")
                .content(asJsonString(updatedInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void deleteNotificationRule() throws Exception {
    String uuid = createNotificationRuleInDb("resourceid", testUserHolder.get()).getId();

    mvc.perform(
            delete(NOTIFICATION_RULE_URI + "/" + uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
    assertTrue(notificationRuleRepository.findById(uuid).isEmpty());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void deleteNotificationRule_WITH_unexisting_id() throws Exception {
    mvc.perform(
            delete(NOTIFICATION_RULE_URI + "/radomid")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void findNotificationRule() throws Exception {
    NotificationRule notificationRule =
        createNotificationRuleInDb("resourceid", testUserHolder.get());

    String response =
        mvc.perform(
                get(NOTIFICATION_RULE_URI + "/" + notificationRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        notificationRule.getSubject(), JsonPath.read(response, "$.notification_rule_subject"));
    assertEquals(
        notificationRule.getTrigger().name(),
        JsonPath.read(response, "$.notification_rule_trigger"));
    assertEquals(
        notificationRule.getResourceId(),
        JsonPath.read(response, "$.notification_rule_resource_id"));
    assertEquals(
        notificationRule.getNotificationResourceType().name(),
        JsonPath.read(response, "$.notification_rule_resource_type"));
    assertEquals(
        notificationRule.getOwner().getId(), JsonPath.read(response, "$.notification_rule_owner"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void findNotificationRuleByResource() throws Exception {

    NotificationRule notificationRule =
        createNotificationRuleInDb("resourceid", testUserHolder.get());

    String response =
        mvc.perform(
                get(NOTIFICATION_RULE_URI + "/resource/" + notificationRule.getResourceId())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        notificationRule.getSubject(), JsonPath.read(response, "$[0].notification_rule_subject"));
    assertEquals(
        notificationRule.getTrigger().name(),
        JsonPath.read(response, "$[0].notification_rule_trigger"));
    assertEquals(
        notificationRule.getResourceId(),
        JsonPath.read(response, "$[0].notification_rule_resource_id"));
    assertEquals(
        notificationRule.getNotificationResourceType().name(),
        JsonPath.read(response, "$[0].notification_rule_resource_type"));
    assertEquals(
        notificationRule.getOwner().getId(),
        JsonPath.read(response, "$[0].notification_rule_owner"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void searchTagRule() throws Exception {
    createNotificationRuleInDb("notificationRule1", testUserHolder.get());
    createNotificationRuleInDb("notificationRule2", testUserHolder.get());
    createNotificationRuleInDb("notificationRule3", testUserHolder.get());
    createNotificationRuleInDb("notificationRule4", testUserHolder.get());

    SearchPaginationInput input = new SearchPaginationInput();
    input.setSize(2);
    input.setPage(0);

    String response =
        mvc.perform(
                post(NOTIFICATION_RULE_URI + "/search")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(Integer.valueOf(2), JsonPath.read(response, "$.numberOfElements"));
    assertEquals(Integer.valueOf(4), JsonPath.read(response, "$.totalElements"));
  }

  private NotificationRule createNotificationRuleInDb(String resourceId, User testUser) {
    return notificationRuleRepository.save(getNotificationRule(resourceId, testUser));
  }

  private NotificationRule getNotificationRule(String resourceId, User testUser) {
    NotificationRule notificationRule = new NotificationRule();
    notificationRule.setOwner(userRepository.findById(testUser.getId()).orElse(null));
    notificationRule.setSubject("subject");
    notificationRule.setNotificationResourceType(NotificationRuleResourceType.SCENARIO);
    notificationRule.setTrigger(NotificationRuleTrigger.DIFFERENCE);
    notificationRule.setType(NotificationRuleType.EMAIL);
    notificationRule.setResourceId(resourceId);
    return notificationRule;
  }
}
