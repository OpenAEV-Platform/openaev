package io.openaev.service.payload_approval;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.database.repository.PayloadApprovalNotificationRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.service.MailingService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload approval notifications (Task 6)")
class PayloadApprovalNotificationTest extends IntegrationTest {

  private static final String TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PayloadApprovalNotificationRepository outbox;
  @Autowired private PayloadApprovalNotificationDispatcher service;
  @MockitoBean private MailingService mailingService;

  private User author;
  private User otherAuthor;
  private User approver;
  private User otherApprover;
  private User bypassHolder;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    domainComposer.reset();
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), TENANT);
    author =
        userWith("Author", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS);
    otherAuthor =
        userWith("Other", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS);
    approver =
        userWith(
            "Approver",
            Capability.ACCESS_THREAT_ARSENALS,
            Capability.MANAGE_THREAT_ARSENALS,
            Capability.APPROVE_THREAT_ARSENALS);
    otherApprover =
        userWith("Second", Capability.ACCESS_THREAT_ARSENALS, Capability.APPROVE_THREAT_ARSENALS);
    bypassHolder = userWith("Bypass", Capability.BYPASS);
    // Nothing recorded by the setup leaks into the tests.
    service.dispatchTenant(TENANT);
  }

  private User userWith(String firstName, Capability... capabilities) {
    User user =
        userComposer
            .forUser(
                UserFixture.getUser(firstName, "User", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(
                tenantGroupComposer
                    .forGroup(TenantGroupFixture.getGroup())
                    .withRole(
                        tenantRoleComposer.forRole(
                            TenantRoleFixture.getRole(new HashSet<>(Set.of(capabilities))))))
            .persist()
            .get();
    tenantRepository.addUserToTenant(user.getId(), TENANT);
    tenantMembershipCacheManager.evict(user.getId(), TENANT);
    return user;
  }

  private String url(String suffix) {
    return tenantUri(TENANT_THREAT_ARSENAL_URL) + suffix;
  }

  private String createAs(User user, String name) throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    var input = ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));
    Map<String, Object> body = new HashMap<>(JsonPath.parse(asJsonString(input)).json());
    body.put("action_name", name);
    String response =
        mvc.perform(
                post(url(""))
                    .with(authentication(buildAuthenticationToken(user)))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(body)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.injector_contract_id");
  }

  private ResultActions editAs(User user, String actionId, String name, String content)
      throws Exception {
    ThreatArsenalActionUpdateInput input =
        new ThreatArsenalActionUpdateInput(
            name,
            new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
            "Description",
            "bash",
            content,
            Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
            new BaseInjectExpectation.EXPECTATION_TYPE[] {},
            Collections.emptyMap(),
            null,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            List.of());
    return mvc.perform(
        put(url("/" + actionId))
            .with(authentication(buildAuthenticationToken(user)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input)));
  }

  private String detail(String actionId) throws Exception {
    return mvc.perform(get(url("/" + actionId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions decideAs(User user, String actionId, String decision, String fingerprint)
      throws Exception {
    Map<String, String> body =
        decision.equals("approve")
            ? Map.of("approval_fingerprint", fingerprint)
            : Map.of("approval_reason", "Too broad");
    return mvc.perform(
        post(url("/" + actionId + "/" + decision))
            .with(authentication(buildAuthenticationToken(user)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(body)));
  }

  private static boolean onlyUser(List<User> users, User expected) {
    return users != null && users.size() == 1 && users.getFirst().getId().equals(expected.getId());
  }

  private List<Notification> notificationsOf(User user) {
    return java.util.stream.StreamSupport.stream(
            notificationRepository.findAll().spliterator(), false)
        .filter(notification -> notification.getUser().getId().equals(user.getId()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> eventsOf(Notification notification) {
    return (List<Map<String, Object>>) notification.getContent().getFirst().get("events");
  }

  private static String titleOf(Notification notification) {
    return (String) notification.getContent().getFirst().get("title");
  }

  @Nested
  @DisplayName("Approvers are notified when a payload goes pending (US6.1)")
  class Approvers {

    @Test
    @DisplayName(
        "Given an author's new action, every explicit approver of the tenant should get one in-app notification linking to it; not the author, an admin or a Bypass holder")
    void given_authorAction_should_notifyExplicitApproversOnly() throws Exception {
      // Arrange
      String actionId = createAs(author, "Whoami");

      // Act
      service.dispatchTenant(TENANT);

      // Assert
      for (User expected : List.of(approver, otherApprover)) {
        List<Notification> received = notificationsOf(expected);
        assertThat(received).hasSize(1);
        Notification notification = received.getFirst();
        assertThat(notification.getName())
            .isEqualTo(PayloadApprovalNotificationDispatcher.REQUESTS_NAME);
        assertThat(titleOf(notification)).isEqualTo("1 payload pending approval");
        Map<String, Object> event = eventsOf(notification).getFirst();
        assertThat(event.get("operation")).isEqualTo("APPROVAL_PENDING");
        assertThat(event.get("resource_type")).isEqualTo("THREAT_ARSENAL");
        assertThat(event.get("resource_id")).isEqualTo(actionId);
        assertThat((String) event.get("message"))
            .isEqualTo("Payload \"Whoami\" is pending approval (submitted by Author User)");
      }
      assertThat(notificationsOf(author)).isEmpty();
      assertThat(notificationsOf(bypassHolder)).isEmpty();
      assertThat(notificationsOf(testUserHolder.get())).isEmpty();
      assertThat(outbox.findByDispatchedAtIsNullOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    @DisplayName(
        "Given three actions going pending within one batch, each approver should get one notification listing the three")
    void given_burst_should_groupIntoOneNotification() throws Exception {
      // Arrange
      createAs(author, "First");
      createAs(author, "Second");
      createAs(otherAuthor, "Third");

      // Act
      service.dispatchTenant(TENANT);

      // Assert
      List<Notification> received = notificationsOf(approver);
      assertThat(received).hasSize(1);
      assertThat(titleOf(received.getFirst())).isEqualTo("3 payloads pending approval");
      assertThat(eventsOf(received.getFirst())).hasSize(3);
    }

    @Test
    @DisplayName(
        "Given an action already pending, editing it again should not notify the approvers again")
    void given_alreadyPending_should_notNotifyAgain() throws Exception {
      // Arrange
      String actionId = createAs(author, "Whoami");
      service.dispatchTenant(TENANT);

      // Act
      editAs(author, actionId, "Whoami", "whoami /all").andExpect(status().is2xxSuccessful());
      service.dispatchTenant(TENANT);

      // Assert
      assertThat(notificationsOf(approver)).hasSize(1);
    }

    @Test
    @DisplayName(
        "Given an approved action, a new version should notify the approvers once, and a second edit replacing it should not")
    void given_newVersion_should_notifyOnce() throws Exception {
      // Arrange
      String actionId = createAs(approver, "Whoami");
      service.dispatchTenant(TENANT);
      assertThat(notificationsOf(otherApprover)).isEmpty();

      // Act
      editAs(author, actionId, "Whoami", "whoami /priv").andExpect(status().is2xxSuccessful());
      editAs(author, actionId, "Whoami", "whoami /groups").andExpect(status().is2xxSuccessful());
      service.dispatchTenant(TENANT);

      // Assert
      List<Notification> received = notificationsOf(otherApprover);
      assertThat(received).hasSize(1);
      assertThat(eventsOf(received.getFirst())).hasSize(1);
      assertThat((String) eventsOf(received.getFirst()).getFirst().get("message"))
          .isEqualTo("New version v2 of \"Whoami\" is pending approval (submitted by Author User)");
      // The same author replacing their own version is not told it was superseded.
      assertThat(notificationsOf(author)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Authors are told the outcome (US6.3)")
  class Outcomes {

    @Test
    @DisplayName("Given a rejected action, its author should be told, with the reason")
    void given_rejectedAction_should_notifyAuthorWithReason() throws Exception {
      // Arrange
      String actionId = createAs(author, "Whoami");

      // Act
      decideAs(approver, actionId, "reject", null).andExpect(status().is2xxSuccessful());
      service.dispatchTenant(TENANT);

      // Assert
      List<Notification> received = notificationsOf(author);
      assertThat(received).hasSize(1);
      assertThat(received.getFirst().getName())
          .isEqualTo(PayloadApprovalNotificationDispatcher.OUTCOMES_NAME);
      Map<String, Object> event = eventsOf(received.getFirst()).getFirst();
      assertThat(event.get("operation")).isEqualTo("REJECTED");
      assertThat((String) event.get("message"))
          .isEqualTo("Your payload \"Whoami\" was rejected by Approver User: Too broad");
    }

    @Test
    @DisplayName("Given an approved version, its author should be told")
    void given_approvedVersion_should_notifyAuthor() throws Exception {
      // Arrange
      String actionId = createAs(approver, "Whoami");
      editAs(author, actionId, "Whoami", "whoami /priv").andExpect(status().is2xxSuccessful());
      String fingerprint =
          JsonPath.read(detail(actionId), "$.action_pending_version.version_fingerprint");

      // Act
      decideAs(otherApprover, actionId, "approve", fingerprint)
          .andExpect(status().is2xxSuccessful());
      service.dispatchTenant(TENANT);

      // Assert
      List<Notification> received = notificationsOf(author);
      assertThat(received).hasSize(1);
      assertThat((String) eventsOf(received.getFirst()).getFirst().get("message"))
          .isEqualTo("Your version v2 of \"Whoami\" was approved by Second User");
    }

    @Test
    @DisplayName(
        "Given a pending version replaced by another author's edit, its author should be told")
    void given_supersededVersion_should_notifyItsAuthor() throws Exception {
      // Arrange
      String actionId = createAs(approver, "Whoami");
      editAs(author, actionId, "Whoami", "whoami /priv").andExpect(status().is2xxSuccessful());

      // Act
      editAs(otherAuthor, actionId, "Whoami", "whoami /groups")
          .andExpect(status().is2xxSuccessful());
      service.dispatchTenant(TENANT);

      // Assert
      List<Notification> received = notificationsOf(author);
      assertThat(received).hasSize(1);
      Map<String, Object> event = eventsOf(received.getFirst()).getFirst();
      assertThat(event.get("operation")).isEqualTo("SUPERSEDED");
      assertThat((String) event.get("message"))
          .isEqualTo(
              "Your version v2 of \"Whoami\" was replaced by a newer version (by Other User)");
    }
  }

  @Nested
  @DisplayName("Preferences (US6.2)")
  class Preferences {

    private void setPreferences(User user, String type, boolean inApp, boolean email)
        throws Exception {
      mvc.perform(
              put("/api/notification-preferences/me")
                  .with(authentication(buildAuthenticationToken(user)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          List.of(
                              Map.of(
                                  "notification_preference_type", type,
                                  "notification_preference_in_app", inApp,
                                  "notification_preference_email", email)))))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given no preference, a user should get the defaults: in-app on, email off")
    void given_noPreference_should_returnDefaults() throws Exception {
      // Act
      String preferences =
          mvc.perform(
                  get("/api/notification-preferences/me")
                      .with(authentication(buildAuthenticationToken(author))))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((List<String>) JsonPath.read(preferences, "$[*].notification_preference_type"))
          .containsExactly("PAYLOAD_APPROVAL_REQUESTS", "PAYLOAD_APPROVAL_OUTCOMES");
      assertThat((List<Boolean>) JsonPath.read(preferences, "$[*].notification_preference_in_app"))
          .containsOnly(true);
      assertThat((List<Boolean>) JsonPath.read(preferences, "$[*].notification_preference_email"))
          .containsOnly(false);
      verify(mailingService, never()).sendEmail(anyString(), anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName(
        "Given an approver who chose email only, a pending action should send one email and no in-app notification; other users keep the defaults")
    void given_emailOnly_should_sendEmailOnly() throws Exception {
      // Arrange
      setPreferences(approver, "PAYLOAD_APPROVAL_REQUESTS", false, true);
      String actionId = createAs(author, "Whoami");

      // Act
      service.dispatchTenant(TENANT);

      // Assert
      assertThat(notificationsOf(approver)).isEmpty();
      verify(mailingService)
          .sendEmail(
              eq("[OpenAEV] " + PayloadApprovalNotificationDispatcher.REQUESTS_NAME),
              org.mockito.ArgumentMatchers.contains("/admin/threat-arsenal?action=" + actionId),
              argThat(users -> onlyUser(users, approver)),
              eq(TENANT));
      assertThat(notificationsOf(otherApprover)).hasSize(1);
      verify(mailingService, never())
          .sendEmail(
              anyString(),
              anyString(),
              argThat(users -> onlyUser(users, otherApprover)),
              anyString());
    }

    @Test
    @DisplayName(
        "Given a preference changed by one user, another user's preferences should stay unchanged")
    void given_onePreferenceChange_should_onlyAffectThatUser() throws Exception {
      // Act
      setPreferences(approver, "PAYLOAD_APPROVAL_OUTCOMES", false, true);

      // Assert
      String others =
          mvc.perform(
                  get("/api/notification-preferences/me")
                      .with(authentication(buildAuthenticationToken(otherApprover))))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat((List<Boolean>) JsonPath.read(others, "$[*].notification_preference_email"))
          .containsOnly(false);
    }
  }
}
