package io.openaev.rest.notification_rule;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.transaction.Transactional;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
public class NotificationRuleApiTest extends IntegrationTest {

  public static final String NOTIFICATION_RULE_URI = "/api/notification-rules";

  @Autowired private MockMvc mvc;

  @Autowired private NotificationRuleRepository notificationRuleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    mvc.perform(
            post(NOTIFICATION_RULE_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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

  // -- TENANT ISOLATION TESTS --
  // Note: NOTIFICATION_RULE uses parent permission (RESOURCES_USING_PARENT_PERMISSION)
  // and has no dedicated capability. Endpoints without resourceId in @AccessControl
  // (e.g. findNotificationRule) cannot resolve the parent for non-admin users.
  // These tests use isAdmin=true to bypass RBAC and validate tenant isolation
  // at the data layer (findByIdAndTenantId / existsByIdAndTenantId).

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  @Transactional
  class TenantIsolation {

    private NotificationRule createNotificationRuleInTenantDb(String tenantId, String scenarioId) {
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);
      NotificationRule rule = new NotificationRule();
      rule.setOwner(userRepository.findById(testUserHolder.get().getId()).orElse(null));
      rule.setSubject("isolation-test-subject");
      rule.setNotificationResourceType(NotificationRuleResourceType.SCENARIO);
      rule.setTrigger(NotificationRuleTrigger.DIFFERENCE);
      rule.setType(NotificationRuleType.EMAIL);
      rule.setResourceId(scenarioId);
      return notificationRuleRepository.save(rule);
    }

    @Test
    @DisplayName("NotificationRule created in tenant X should NOT be readable from tenant Y")
    void given_notificationRuleInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      NotificationRule rule = createNotificationRuleInTenantDb(tenantX.getId(), "fake-scenario-id");

      entityManager.flush();
      entityManager.clear();

      // Act — read from tenant Y
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/notification-rules/" + rule.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — should return empty (findById returns null via .orElse(null))
      assertEquals("", response);
    }

    @Test
    @DisplayName("NotificationRule created in tenant X should be readable from tenant X")
    void given_notificationRuleInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");

      NotificationRule rule = createNotificationRuleInTenantDb(tenantX.getId(), "fake-scenario-id");

      entityManager.flush();
      entityManager.clear();

      // Act & Assert — read from same tenant should succeed
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/notification-rules/" + rule.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals(
          "isolation-test-subject", JsonPath.read(response, "$.notification_rule_subject"));
    }

    @Test
    @DisplayName("NotificationRule created in tenant X should NOT be updatable from tenant Y")
    void given_notificationRuleInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      NotificationRule rule = createNotificationRuleInTenantDb(tenantX.getId(), "fake-scenario-id");

      entityManager.flush();
      entityManager.clear();

      // Act — update from tenant Y
      UpdateNotificationRuleInput updateInput =
          UpdateNotificationRuleInput.builder().subject("hijacked").build();

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/notification-rules/" + rule.getId())
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("NotificationRule created in tenant X should NOT be deletable from tenant Y")
    void given_notificationRuleInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      NotificationRule rule = createNotificationRuleInTenantDb(tenantX.getId(), "fake-scenario-id");

      entityManager.flush();
      entityManager.clear();

      // Act — delete from tenant Y
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/notification-rules/" + rule.getId())
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
