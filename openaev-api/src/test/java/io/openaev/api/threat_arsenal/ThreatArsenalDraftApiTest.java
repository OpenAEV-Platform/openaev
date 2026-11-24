package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.PayloadDraftInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalRejectInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat Arsenal payload drafts")
class ThreatArsenalDraftApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private DomainComposer domainComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  private Authentication author;
  private Authentication approver;
  private Authentication reader;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    domainComposer.reset();
    injectorContractComposer.reset();
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    author =
        buildAuthenticationToken(
            userWith(
                "Author", Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS));
    approver =
        buildAuthenticationToken(
            userWith(
                "Approver",
                Capability.ACCESS_THREAT_ARSENALS,
                Capability.MANAGE_THREAT_ARSENALS,
                Capability.APPROVE_THREAT_ARSENALS));
    reader = buildAuthenticationToken(userWith("Reader", Capability.ACCESS_THREAT_ARSENALS));
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
    tenantRepository.addUserToTenant(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantMembershipCacheManager.evict(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    return user;
  }

  private String url(String suffix) {
    return tenantUri(TENANT_THREAT_ARSENAL_URL) + suffix;
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  /** Creates a command action as the given user and returns its action id. */
  private String createAction(Authentication as) throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    ThreatArsenalActionCreateInput input =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(
            List.of(domain.getId()), "Draft action " + UUID.randomUUID());
    String response =
        body(
            mvc.perform(
                    post(url(""))
                        .with(authentication(as))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input)))
                .andExpect(status().is2xxSuccessful()));
    return JsonPath.read(response, "$.injector_contract_id");
  }

  private String detail(String actionId) throws Exception {
    return body(mvc.perform(get(url("/" + actionId))).andExpect(status().isOk()));
  }

  private String approvalStatus(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.action_approval_status");
  }

  private ThreatArsenalActionUpdateInput update(String actionId, String content) throws Exception {
    return new ThreatArsenalActionUpdateInput(
        JsonPath.read(detail(actionId), "$.action_labels.en"),
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        null,
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
  }

  private String updateAs(Authentication as, String actionId, ThreatArsenalActionUpdateInput input)
      throws Exception {
    return JsonPath.read(
        body(
            mvc.perform(
                    put(url("/" + actionId))
                        .with(authentication(as))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input)))
                .andExpect(status().is2xxSuccessful())),
        "$.injector_contract_id");
  }

  private PayloadDraftInput draftContent(String command) {
    return new PayloadDraftInput(mapper.createObjectNode().put("action_content", command));
  }

  private ResultActions createDraftAs(Authentication as, String actionId, String command)
      throws Exception {
    return mvc.perform(
        post(url("/" + actionId + "/drafts"))
            .with(authentication(as))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(draftContent(command))));
  }

  private String createDraft(Authentication as, String actionId, String command) throws Exception {
    return JsonPath.read(
        body(createDraftAs(as, actionId, command).andExpect(status().isOk())), "$.draft_id");
  }

  private String publish(
      Authentication as, String actionId, String draftId, ThreatArsenalActionUpdateInput input)
      throws Exception {
    return JsonPath.read(
        body(
            mvc.perform(
                    post(url("/" + actionId + "/drafts/" + draftId + "/publish"))
                        .with(authentication(as))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input)))
                .andExpect(status().isOk())),
        "$.injector_contract_id");
  }

  private String drafts(String actionId) throws Exception {
    return body(mvc.perform(get(url("/" + actionId + "/drafts"))).andExpect(status().isOk()));
  }

  private List<String> versions(String actionId) throws Exception {
    return JsonPath.read(
        body(mvc.perform(get(url("/" + actionId + "/versions"))).andExpect(status().isOk())),
        "$[*].payload_version");
  }

  private String history(String actionId) throws Exception {
    return body(mvc.perform(get(url("/" + actionId + "/history"))).andExpect(status().isOk()));
  }

  private void reject(String actionId) throws Exception {
    mvc.perform(
            post(url("/" + actionId + "/reject"))
                .with(authentication(approver))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new ThreatArsenalRejectInput("Deletes system logs"))))
        .andExpect(status().isOk());
  }

  @Nested
  @DisplayName("Saving a draft")
  class Saving {

    @Test
    @DisplayName("a draft is saved as often as needed and never adds a version")
    void given_savedDraft_should_notAddVersion() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String draftId = createDraft(author, actionId, "echo first");

      // Act
      String saved =
          body(
              mvc.perform(
                      put(url("/" + actionId + "/drafts/" + draftId))
                          .with(authentication(author))
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(draftContent("echo second"))))
                  .andExpect(status().isOk()));

      // Assert
      assertThat((String) JsonPath.read(saved, "$.draft_content.action_content"))
          .isEqualTo("echo second");
      assertThat(versions(actionId)).hasSize(1);
      String list = drafts(actionId);
      assertThat((List<String>) JsonPath.read(list, "$[*].draft_id")).containsExactly(draftId);
      assertThat((String) JsonPath.read(list, "$[0].draft_source_version")).isEqualTo("1.0.0");
      assertThat((String) JsonPath.read(list, "$[0].draft_source_action_id")).isEqualTo(actionId);
      assertThat((Boolean) JsonPath.read(list, "$[0].draft_rework")).isFalse();
      assertThat((String) JsonPath.read(list, "$[0].draft_updated_by")).isEqualTo("Author User");
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("everyone who can update the action sees its drafts, a reader cannot write one")
    void given_reader_should_beForbiddenToWriteDrafts() throws Exception {
      // Arrange
      String actionId = createAction(approver);

      // Act / Assert
      createDraftAs(reader, actionId, "echo nope").andExpect(status().isForbidden());
      String draftId = createDraft(author, actionId, "echo shared");
      assertThat((List<String>) JsonPath.read(drafts(actionId), "$[*].draft_id"))
          .containsExactly(draftId);
    }

    @Test
    @DisplayName("a draft of another action is not found")
    void given_draftOfAnotherAction_should_beNotFound() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String otherActionId = createAction(approver);
      String draftId = createDraft(approver, otherActionId, "echo other");

      // Act / Assert
      mvc.perform(
              put(url("/" + actionId + "/drafts/" + draftId))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo hijack"))))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a discarded draft is gone, the versions are unchanged and the history says so")
    void given_discardedDraft_should_keepVersions() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String draftId = createDraft(author, actionId, "echo dropped");

      // Act
      mvc.perform(
              delete(url("/" + actionId + "/drafts/" + draftId))
                  .with(authentication(author))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat((List<String>) JsonPath.read(drafts(actionId), "$[*]")).isEmpty();
      assertThat(versions(actionId)).containsExactly("1.0.0");
      assertThat((List<String>) JsonPath.read(history(actionId), "$[*].content_history_action"))
          .contains("DRAFT_CREATE", "DRAFT_DISCARD");
    }
  }

  @Nested
  @DisplayName("Publishing a draft")
  class Publishing {

    @Test
    @DisplayName(
        "an approver publishes a draft of an older version: a new usable version after the"
            + " highest, its history keeps the version it came from")
    void given_approverPublishesDraftOfOlderVersion_should_addApprovedVersion() throws Exception {
      // Arrange
      String v1 = createAction(approver);
      String v2 = updateAs(approver, v1, update(v1, "echo second version"));
      String draftId = createDraft(approver, v1, "echo from the first");

      // Act
      String published = publish(approver, v1, draftId, update(v1, "echo from the first"));

      // Assert
      assertThat(published).isNotIn(v1, v2);
      assertThat(approvalStatus(published)).isEqualTo("APPROVED");
      assertThat(versions(v1)).hasSize(3);
      assertThat((List<String>) JsonPath.read(drafts(v1), "$[*]")).isEmpty();
      String history = history(published);
      List<String> fromDraft =
          JsonPath.read(
              history,
              "$[?(@.content_history_action == 'UPDATE' && @.content_history_details.draft =="
                  + " true)].content_history_from_version");
      assertThat(fromDraft).containsExactly("1.0.0");
    }

    @Test
    @DisplayName("an author's published draft waits for approval, the approved version stays")
    void given_authorPublishesDraft_should_bePending() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String draftId = createDraft(author, actionId, "echo by the author");

      // Act
      String published = publish(author, actionId, draftId, update(actionId, "echo by the author"));

      // Assert
      assertThat(published).isNotEqualTo(actionId);
      assertThat(approvalStatus(published)).isEqualTo("PENDING");
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }
  }

  @Nested
  @DisplayName("Fixing a rejected version")
  class Rework {

    @Test
    @DisplayName(
        "a rejected version fixed in a draft is sent again under the same number, pending again")
    void given_rejectedVersion_should_beSentAgainUnderSameNumber() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);
      String draft = body(createDraftAs(author, actionId, "echo fixed").andExpect(status().isOk()));
      assertThat((Boolean) JsonPath.read(draft, "$.draft_rework")).isTrue();
      String draftId = JsonPath.read(draft, "$.draft_id");

      // Act
      String published = publish(author, actionId, draftId, update(actionId, "echo fixed"));

      // Assert
      assertThat(published).isEqualTo(actionId);
      assertThat(versions(actionId)).containsExactly("1.0.0");
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
      assertThat((String) JsonPath.read(detail(actionId), "$.command_content"))
          .isEqualTo("echo fixed");
      assertThat((List<String>) JsonPath.read(history(actionId), "$[*].content_history_action"))
          .contains("REWORK", "DRAFT_CREATE");
    }

    @Test
    @DisplayName("an approver's fix of a rejected version is approved at once")
    void given_approverFixesRejectedVersion_should_beApproved() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);
      String draftId = createDraft(approver, actionId, "echo fixed");

      // Act
      publish(approver, actionId, draftId, update(actionId, "echo fixed"));

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("a rejected version is fixed in one draft at most")
    void given_secondDraftOfRejectedVersion_should_refuse() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);
      createDraft(author, actionId, "echo first fix");

      // Act / Assert
      createDraftAs(author, actionId, "echo second fix").andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Notifying the approvers of a fixed version")
  class ReworkNotification {

    // The pending notifications (outbox rows for the approvers) of the action's payload version,
    // read in raw JDBC: the outbox is a tenant-v2 table and a raw read is never rewritten.
    private List<String> pendingNotificationActors(String actionId) {
      entityManager.flush();
      return entityManager
          .unwrap(Session.class)
          .doReturningWork(
              connection -> {
                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "SELECT payload_approval_notification_actor_name"
                            + " FROM payload_approval_notifications WHERE payload_id ="
                            + " (SELECT injector_contract_payload FROM injectors_contracts"
                            + " WHERE injector_contract_id = ?)"
                            + " AND payload_approval_notification_event IN"
                            + " ('PAYLOAD_PENDING', 'VERSION_PENDING')"
                            + " ORDER BY payload_approval_notification_created_at")) {
                  statement.setString(1, actionId);
                  List<String> actors = new ArrayList<>();
                  try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                      actors.add(rows.getString(1));
                    }
                  }
                  return actors;
                }
              });
    }

    @Test
    @DisplayName(
        "given an author without Approve content sending a fixed rejected version again, should"
            + " notify the approvers that it waits for approval")
    void given_authorResubmitsRejectedVersion_should_notifyApprovers() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);
      List<String> before = pendingNotificationActors(actionId);
      String draftId = createDraft(author, actionId, "echo fixed");

      // Act
      publish(author, actionId, draftId, update(actionId, "echo fixed"));

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
      List<String> after = pendingNotificationActors(actionId);
      assertThat(after).hasSize(before.size() + 1);
      assertThat(after.getLast()).isEqualTo("Author User");
    }

    @Test
    @DisplayName(
        "given an approver fixing a rejected version, should approve it at once and notify no"
            + " approver")
    void given_approverResubmitsRejectedVersion_should_notNotifyApprovers() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);
      List<String> before = pendingNotificationActors(actionId);
      String draftId = createDraft(approver, actionId, "echo fixed");

      // Act
      publish(approver, actionId, draftId, update(actionId, "echo fixed"));

      // Assert
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
      assertThat(pendingNotificationActors(actionId)).isEqualTo(before);
    }
  }

  // -- Drafts of new actions, my drafts, rejection reasons, the Draft filter --

  private ThreatArsenalActionCreateInput newActionInput(String name) {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    return ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()), name);
  }

  private PayloadDraftInput newActionContent(String name) {
    return new PayloadDraftInput(mapper.createObjectNode().put("action_name", name));
  }

  private String createNewActionDraft(Authentication as, String name) throws Exception {
    return JsonPath.read(
        body(
            mvc.perform(
                    post(url("/new_action_drafts"))
                        .with(authentication(as))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(newActionContent(name))))
                .andExpect(status().isOk())),
        "$.draft_id");
  }

  private String newActionDrafts(Authentication as) throws Exception {
    return body(
        mvc.perform(get(url("/new_action_drafts")).with(authentication(as)))
            .andExpect(status().isOk()));
  }

  private String myDrafts(Authentication as) throws Exception {
    return body(
        mvc.perform(get(url("/my_drafts")).with(authentication(as))).andExpect(status().isOk()));
  }

  private String publishNewActionDraft(Authentication as, String draftId, String name)
      throws Exception {
    return JsonPath.read(
        body(
            mvc.perform(
                    post(url("/new_action_drafts/" + draftId + "/publish"))
                        .with(authentication(as))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(newActionInput(name))))
                .andExpect(status().isOk())),
        "$.injector_contract_id");
  }

  private SearchPaginationInput approvalFilter(String... values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("action_payload_approval_status");
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.or);
    filter.setValues(List.of(values));
    Filters.FilterGroup group = new Filters.FilterGroup();
    group.setMode(Filters.FilterMode.and);
    group.setFilters(new ArrayList<>(List.of(filter)));
    return PaginationFixture.getDefault().size(1000).filterGroup(group).build();
  }

  private List<String> searchIds(SearchPaginationInput input) throws Exception {
    return JsonPath.read(
        body(
            mvc.perform(
                    post(url("/search"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input)))
                .andExpect(status().isOk())),
        "$.content[*].injector_contract_id");
  }

  @Nested
  @DisplayName("Saving a new action as a draft")
  class NewAction {

    @Test
    @DisplayName(
        "given an author saving the Create form as a draft, should keep it without any action or"
            + " version, and save it again")
    void given_newActionDraft_should_createNoAction() throws Exception {
      // Arrange
      String name = "New action draft " + UUID.randomUUID();
      List<String> before = searchIds(PaginationFixture.getDefault().size(1000).build());

      // Act
      String draftId = createNewActionDraft(author, name);
      String saved =
          body(
              mvc.perform(
                      put(url("/new_action_drafts/" + draftId))
                          .with(authentication(author))
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(newActionContent(name + " v2"))))
                  .andExpect(status().isOk()));

      // Assert
      assertThat((String) JsonPath.read(saved, "$.draft_content.action_name"))
          .isEqualTo(name + " v2");
      assertThat((Boolean) JsonPath.read(saved, "$.draft_new_action")).isTrue();
      assertThat((String) JsonPath.read(saved, "$.draft_action_id")).isNull();
      assertThat((List<String>) JsonPath.read(newActionDrafts(approver), "$[*].draft_id"))
          .contains(draftId);
      assertThat(searchIds(PaginationFixture.getDefault().size(1000).build()))
          .containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    @DisplayName(
        "given an author publishing the draft of a new action, should create v1.0.0 pending and"
            + " drop the draft")
    void given_authorPublishesNewActionDraft_should_createPendingFirstVersion() throws Exception {
      // Arrange
      String name = "Published new action " + UUID.randomUUID();
      String draftId = createNewActionDraft(author, name);

      // Act
      String actionId = publishNewActionDraft(author, draftId, name);

      // Assert
      assertThat(versions(actionId)).containsExactly("1.0.0");
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
      assertThat((List<String>) JsonPath.read(newActionDrafts(author), "$[*].draft_id"))
          .doesNotContain(draftId);
    }

    @Test
    @DisplayName("given an approver publishing the draft of a new action, should approve v1.0.0")
    void given_approverPublishesNewActionDraft_should_approveFirstVersion() throws Exception {
      // Arrange
      String name = "Approved new action " + UUID.randomUUID();
      String draftId = createNewActionDraft(approver, name);

      // Act
      String actionId = publishNewActionDraft(approver, draftId, name);

      // Assert
      assertThat(versions(actionId)).containsExactly("1.0.0");
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("given a discarded new action draft, should be gone")
    void given_discardedNewActionDraft_should_beGone() throws Exception {
      // Arrange
      String draftId = createNewActionDraft(author, "Discarded " + UUID.randomUUID());

      // Act
      mvc.perform(
              delete(url("/new_action_drafts/" + draftId))
                  .with(authentication(author))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat((List<String>) JsonPath.read(newActionDrafts(author), "$[*].draft_id"))
          .doesNotContain(draftId);
    }

    @Test
    @DisplayName("given a user who cannot create actions, should be forbidden")
    void given_reader_should_beForbidden() throws Exception {
      // Act / Assert
      mvc.perform(
              post(url("/new_action_drafts"))
                  .with(authentication(reader))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newActionContent("Forbidden"))))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "given the draft of an action, should not be found among the drafts of new actions, and"
            + " the other way round")
    void given_draftOfOtherKind_should_beNotFound() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String actionDraft = createDraft(author, actionId, "echo existing");
      String newActionDraft = createNewActionDraft(author, "Other kind " + UUID.randomUUID());

      // Act / Assert
      mvc.perform(
              put(url("/new_action_drafts/" + actionDraft))
                  .with(authentication(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(newActionContent("hijack"))))
          .andExpect(status().isNotFound());
      mvc.perform(
              put(url("/" + actionId + "/drafts/" + newActionDraft))
                  .with(authentication(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo hijack"))))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Finding my drafts")
  class MyDrafts {

    @Test
    @DisplayName(
        "given drafts of a new action and of an existing action saved by an author, should list"
            + " both for that author only, with the action to open")
    void given_authorDrafts_should_listOwnDraftsOnly() throws Exception {
      // Arrange
      String actionId = createAction(approver);
      String actionDraft = createDraft(author, actionId, "echo mine");
      String newActionDraft = createNewActionDraft(author, "Mine " + UUID.randomUUID());
      String othersDraft = createNewActionDraft(approver, "Not mine " + UUID.randomUUID());

      // Act
      String mine = myDrafts(author);

      // Assert
      assertThat((List<String>) JsonPath.read(mine, "$[*].draft_id"))
          .contains(actionDraft, newActionDraft)
          .doesNotContain(othersDraft);
      assertThat(
              (List<String>)
                  JsonPath.read(mine, "$[?(@.draft_id == '" + actionDraft + "')].draft_action_id"))
          .containsExactly(actionId);
      assertThat(
              (List<Boolean>)
                  JsonPath.read(
                      mine, "$[?(@.draft_id == '" + newActionDraft + "')].draft_new_action"))
          .containsExactly(true);
    }
  }

  @Nested
  @DisplayName("Showing why a version was rejected")
  class RejectionReason {

    @Test
    @DisplayName(
        "given a rejected version, should carry the rejection reason on the version and on its"
            + " fix draft")
    void given_rejectedVersion_should_carryReason() throws Exception {
      // Arrange
      String actionId = createAction(author);
      reject(actionId);

      // Act
      String draftId = createDraft(author, actionId, "echo fixed");

      // Assert
      String versionsBody =
          body(mvc.perform(get(url("/" + actionId + "/versions"))).andExpect(status().isOk()));
      assertThat((String) JsonPath.read(versionsBody, "$[0].payload_rejection_reason"))
          .isEqualTo("Deletes system logs");
      String list = drafts(actionId);
      assertThat((String) JsonPath.read(list, "$[0].draft_id")).isEqualTo(draftId);
      assertThat((String) JsonPath.read(list, "$[0].draft_rework_reason"))
          .isEqualTo("Deletes system logs");
    }
  }

  @Nested
  @DisplayName("Filtering the actions with a draft")
  class DraftFilter {

    @Test
    @DisplayName(
        "given the Draft value of the Approval filter, should list only the actions with a draft,"
            + " or with one of the other ticked statuses, and count them")
    void given_draftFilter_should_listActionsWithDraft() throws Exception {
      // Arrange
      String withDraft = createAction(approver);
      createDraft(author, withDraft, "echo draft");
      String approvedOnly = createAction(approver);
      String pending = createAction(author);

      // Act
      List<String> drafts = searchIds(approvalFilter("DRAFT"));
      List<String> draftsOrPending = searchIds(approvalFilter("PENDING", "DRAFT"));

      // Assert
      assertThat(drafts).contains(withDraft).doesNotContain(approvedOnly, pending);
      assertThat(draftsOrPending).contains(withDraft, pending).doesNotContain(approvedOnly);
      String counts =
          body(
              mvc.perform(
                      post(url("/facet-counts"))
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(PaginationFixture.getDefault().build())))
                  .andExpect(status().isOk()));
      assertThat(((Number) JsonPath.read(counts, "$.approvals.DRAFT")).longValue())
          .isGreaterThanOrEqualTo(1);
    }
  }
}
