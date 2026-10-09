package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalApproveInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalRejectInput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.migration.V6_20261008160000000__Grant_approve_content_to_threat_arsenal_managers;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.stream.Collectors;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat Arsenal payload approval")
class ThreatArsenalApprovalApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadService payloadService;
  @Autowired private PayloadApprovalService payloadApprovalService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @Autowired
  private V6_20261008160000000__Grant_approve_content_to_threat_arsenal_managers
      approveContentUpgrade;

  private Authentication author;
  private Authentication approver;

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
    // Holds Manage too, so it can author the actions whose auto-approval the tests check.
    approver =
        buildAuthenticationToken(
            userWith(
                "Approver",
                Capability.ACCESS_THREAT_ARSENALS,
                Capability.MANAGE_THREAT_ARSENALS,
                Capability.APPROVE_THREAT_ARSENALS));
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

  /** Creates a command action as the given user and returns its action id. */
  private String createAction(Authentication as) throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    ThreatArsenalActionCreateInput input =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));
    String response =
        mvc.perform(
                post(url(""))
                    .with(authentication(as))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.injector_contract_id");
  }

  private String detail(String actionId) throws Exception {
    return mvc.perform(get(url("/" + actionId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String approvalStatus(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.action_approval_status");
  }

  private String fingerprint(String actionId) throws Exception {
    return JsonPath.read(detail(actionId), "$.action_approval_fingerprint");
  }

  private ThreatArsenalActionUpdateInput update(String content, String description) {
    return new ThreatArsenalActionUpdateInput(
        "Command line payload",
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        description,
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

  private void updateAs(Authentication as, String actionId, ThreatArsenalActionUpdateInput input)
      throws Exception {
    mvc.perform(
            put(url("/" + actionId))
                .with(authentication(as))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @DisplayName("Status computed on write")
  class Writes {

    @Test
    @DisplayName("an author's new action is pending, an approver's is auto-approved and recorded")
    void given_creators_should_computeStatus() throws Exception {
      String byAuthor = createAction(author);
      String byApprover = createAction(approver);

      assertThat(approvalStatus(byAuthor)).isEqualTo("PENDING");
      String approverDetail = detail(byApprover);
      assertThat((String) JsonPath.read(approverDetail, "$.action_approval_status"))
          .isEqualTo("APPROVED");
      assertThat(
              (Boolean)
                  JsonPath.read(approverDetail, "$.action_approval_latest.approval_automatic"))
          .isTrue();
      assertThat(
              (String)
                  JsonPath.read(approverDetail, "$.action_approval_latest.approval_actor_name"))
          .isEqualTo("Approver User");
    }

    @Test
    @DisplayName(
        "an author's content edit sends an approved action back to pending, a cosmetic one does not")
    void given_authorEdits_should_onlyResetOnContentChange() throws Exception {
      String actionId = createAction(approver);

      updateAs(author, actionId, update("echo hello", "A clearer description"));
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");

      updateAs(author, actionId, update("echo changed", "A clearer description"));
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a payload pushed by a collector is pending")
    void given_collectorUpsert_should_bePending() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadUpsertInput upsertInput =
          PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of(domain));
      upsertInput.setExternalId("approval-collector-payload");
      String created =
          mvc.perform(
                  post(PAYLOAD_URI + "/upsert")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(upsertInput))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(created, "$.payload_approval_status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a duplicate by an approver is auto-approved, by an author pending")
    void given_duplicates_should_followTheActor() throws Exception {
      String origin = createAction(approver);

      String byAuthor =
          mvc.perform(
                  post(url("/" + origin + "/duplicate")).with(authentication(author)).with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String byApprover =
          mvc.perform(
                  post(url("/" + origin + "/duplicate"))
                      .with(authentication(approver))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(approvalStatus(JsonPath.read(byAuthor, "$.injector_contract_id")))
          .isEqualTo("PENDING");
      assertThat(approvalStatus(JsonPath.read(byApprover, "$.injector_contract_id")))
          .isEqualTo("APPROVED");
    }
  }

  @Nested
  @DisplayName("Approve and reject")
  class Decisions {

    @Test
    @DisplayName("an approver approves a pending action with the shown fingerprint")
    void given_approver_should_approve() throws Exception {
      String actionId = createAction(author);

      String response =
          mvc.perform(
                  post(url("/" + actionId + "/approve"))
                      .with(authentication(approver))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          asJsonString(new ThreatArsenalApproveInput(fingerprint(actionId), "OK"))))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(response, "$.action_approval_status"))
          .isEqualTo("APPROVED");
      assertThat((String) JsonPath.read(response, "$.action_approval_latest.approval_origin"))
          .isEqualTo("APPROVE");
      assertThat((String) JsonPath.read(response, "$.action_approval_latest.approval_comment"))
          .isEqualTo("OK");
    }

    @Test
    @DisplayName("an author cannot approve, even their own action")
    void given_author_should_beForbidden() throws Exception {
      String actionId = createAction(author);

      mvc.perform(
              post(url("/" + actionId + "/approve"))
                  .with(authentication(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(new ThreatArsenalApproveInput(fingerprint(actionId), null))))
          .andExpect(status().isForbidden());
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName(
        "approving content that changed since it was shown, or a non-pending action, is refused")
    void given_staleFingerprintOrNotPending_should_refuse() throws Exception {
      String actionId = createAction(author);
      String shown = fingerprint(actionId);
      updateAs(author, actionId, update("echo swapped", null));

      mvc.perform(
              post(url("/" + actionId + "/approve"))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ThreatArsenalApproveInput(shown, null))))
          .andExpect(status().isBadRequest());

      String approved = createAction(approver);
      mvc.perform(
              post(url("/" + approved + "/approve"))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(new ThreatArsenalApproveInput(fingerprint(approved), null))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejecting needs a reason; the history keeps every step, newest first")
    void given_reject_should_requireReasonAndRecordHistory() throws Exception {
      String actionId = createAction(author);

      mvc.perform(
              post(url("/" + actionId + "/reject"))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ThreatArsenalRejectInput(""))))
          .andExpect(status().isBadRequest());

      mvc.perform(
              post(url("/" + actionId + "/reject"))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ThreatArsenalRejectInput("Deletes system logs"))))
          .andExpect(status().isOk());
      assertThat(approvalStatus(actionId)).isEqualTo("REJECTED");

      // Rule 3: a rejected payload goes back to pending once its author edits it.
      updateAs(author, actionId, update("echo fixed", null));
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");

      String history =
          mvc.perform(get(url("/" + actionId + "/approvals")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> origins = JsonPath.read(history, "$[*].approval_origin");
      assertThat(origins).containsExactly("UPDATE", "REJECT", "CREATE");
      assertThat((String) JsonPath.read(history, "$[1].approval_comment"))
          .isEqualTo("Deletes system logs");
    }

    @Test
    @DisplayName("a payload-less built-in action cannot be approved: it needs no approval")
    void given_payloadLessAction_should_refuse() throws Exception {
      InjectorContract contract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .persist()
              .get();

      mvc.perform(
              post(url("/" + contract.getId() + "/approve"))
                  .with(authentication(approver))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ThreatArsenalApproveInput("any", null))))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("List column, filter and facet")
  class ListAndFilter {

    @Test
    @DisplayName("the list carries the status, the approval filter and facet counts work")
    void given_approvalFilter_should_returnMatchingActions() throws Exception {
      String pending = createAction(author);
      String approved = createAction(approver);

      Filters.Filter filter = new Filters.Filter();
      filter.setKey("action_payload_approval_status");
      filter.setOperator(Filters.FilterOperator.eq);
      filter.setMode(Filters.FilterMode.or);
      filter.setValues(List.of("PENDING"));
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(new ArrayList<>(List.of(filter)));
      SearchPaginationInput input =
          PaginationFixture.getDefault().size(1000).filterGroup(filterGroup).build();
      String page =
          mvc.perform(
                  post(url("/search"))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> ids = JsonPath.read(page, "$.content[*].injector_contract_id");
      List<String> statuses =
          JsonPath.read(page, "$.content[*].action_payload.payload_approval_status");
      assertThat(ids).contains(pending).doesNotContain(approved);
      assertThat(statuses).containsOnly("PENDING");

      String counts =
          mvc.perform(
                  post(url("/facet-counts"))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(PaginationFixture.getDefault().build())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(((Number) JsonPath.read(counts, "$.approvals.PENDING")).longValue())
          .isGreaterThanOrEqualTo(1);
      assertThat(((Number) JsonPath.read(counts, "$.approvals.APPROVED")).longValue())
          .isGreaterThanOrEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Upgrade: existing authors approve, maker-checker is opt-in (Task 3)")
  class UpgradeOptIn {

    private void runUpgrade() {
      entityManager.flush();
      entityManager
          .unwrap(Session.class)
          .doWork(
              connection -> {
                try {
                  approveContentUpgrade.migrate(
                      new Context() {
                        @Override
                        public Configuration getConfiguration() {
                          return null;
                        }

                        @Override
                        public java.sql.Connection getConnection() {
                          return connection;
                        }
                      });
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      entityManager.clear();
    }

    @Test
    @DisplayName(
        "an upgraded author is auto-approved; once approve content is removed from their role,"
            + " their content edit makes the action pending")
    void given_upgradedAuthorThenCapabilityRemoved_should_requireApproval() throws Exception {
      // -- ARRANGE --
      TenantRoleComposer.Composer authorsRoleComposer =
          tenantRoleComposer.forRole(
              TenantRoleFixture.getRole(
                  new HashSet<>(
                      Set.of(
                          Capability.ACCESS_THREAT_ARSENALS, Capability.MANAGE_THREAT_ARSENALS))));
      User upgradedAuthor =
          userComposer
              .forUser(
                  UserFixture.getUser(
                      "Upgraded", "Author", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(
                  tenantGroupComposer
                      .forGroup(TenantGroupFixture.getGroup())
                      .withRole(authorsRoleComposer))
              .persist()
              .get();
      Role authorsRole = authorsRoleComposer.get();
      tenantRepository.addUserToTenant(upgradedAuthor.getId(), Tenant.DEFAULT_TENANT_UUID);
      tenantMembershipCacheManager.evict(upgradedAuthor.getId(), Tenant.DEFAULT_TENANT_UUID);

      // -- ACT: upgrade --
      runUpgrade();
      Authentication asAuthor = buildAuthenticationToken(upgradedAuthor);
      String actionId = createAction(asAuthor);

      // -- ASSERT: nothing changes for an existing author --
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");

      // -- ACT: an admin turns maker-checker on for this role --
      mvc.perform(
              put(tenantUri("/api/tenants/{tenantId}/roles/") + authorsRole.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          new RoleInput(
                              authorsRole.getName(),
                              null,
                              Set.of(
                                  Capability.ACCESS_THREAT_ARSENALS,
                                  Capability.MANAGE_THREAT_ARSENALS)))))
          .andExpect(status().is2xxSuccessful());
      updateAs(asAuthor, actionId, update("echo maker-checker", "Command line payload"));

      // -- ASSERT --
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }
  }

  @Nested
  @DisplayName("System-generated payloads are approved (Task 4, US4.1)")
  class SystemGeneratedPayloads {

    private final TxCtx defaultTenant = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);

    private String newDocument() {
      return documentRepository.save(DocumentFixture.getDocumentJpeg()).getId();
    }

    private Set<String> entryIds(Payload payload) {
      return payloadApprovalService.history(payload.getId()).stream()
          .map(PayloadApproval::getId)
          .collect(Collectors.toSet());
    }

    private PayloadApproval latestEntry(Payload payload) {
      return payloadApprovalService.latest(payload.getId()).orElseThrow();
    }

    /** A file drop created the way it was before Task 4: pending, never written by a human. */
    private FileDrop legacyPendingFileDrop(String documentId) {
      // Created in its own tenant-scoped transaction: set the legacy state on the persisted row
      String id = payloadService.createFileDropPayload(defaultTenant, documentId).getId();
      FileDrop fileDrop = (FileDrop) payloadRepository.findById(id).orElseThrow();
      payloadApprovalService.onWrite(fileDrop, null, PayloadApproval.ORIGIN.IMPORT, null);
      payloadRepository.save(fileDrop);
      entityManager.flush();
      assertThat(fileDrop.getApprovalStatus()).isEqualTo(Payload.PAYLOAD_APPROVAL_STATUS.PENDING);
      return fileDrop;
    }

    @Test
    @DisplayName("a security-coverage file drop is approved, recorded as a system approval")
    void given_securityCoverageFileDrop_should_beApprovedAsSystem() {
      // -- ACT --
      FileDrop fileDrop = payloadService.createFileDropPayload(defaultTenant, newDocument());

      // -- ASSERT --
      assertThat(fileDrop.getApprovalStatus()).isEqualTo(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED);
      PayloadApproval entry = latestEntry(fileDrop);
      assertThat(entry.getOrigin()).isEqualTo(PayloadApproval.ORIGIN.SYSTEM);
      assertThat(entry.isAutomatic()).isTrue();
      assertThat(entry.getActor()).isNull();
    }

    @Test
    @DisplayName(
        "a pending file drop never written by a human is approved when the coverage reuses it")
    void given_legacyPendingFileDrop_should_beApprovedOnReuse() {
      // -- ARRANGE --
      String documentId = newDocument();
      FileDrop legacy = legacyPendingFileDrop(documentId);
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      Set<String> entriesBefore = entryIds(legacy);

      // -- ACT --
      FileDrop reused =
          payloadService.getFileDropPayloadByDocument(defaultTenant, documentId, scenario);

      // -- ASSERT --
      assertThat(reused.getId()).isEqualTo(legacy.getId());
      assertThat(reused.getApprovalStatus()).isEqualTo(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED);
      // Entries written in the same instant cannot be ordered by time: compare by id instead
      List<PayloadApproval> added =
          payloadApprovalService.history(reused.getId()).stream()
              .filter(entry -> !entriesBefore.contains(entry.getId()))
              .toList();
      assertThat(added).hasSize(1);
      assertThat(added.getFirst().getOrigin()).isEqualTo(PayloadApproval.ORIGIN.SYSTEM);
      assertThat(added.getFirst().isAutomatic()).isTrue();
    }

    @Test
    @DisplayName("a pending file drop a human wrote stays pending when the coverage reuses it")
    void given_humanWrittenPendingFileDrop_should_stayPendingOnReuse() {
      // -- ARRANGE --
      String documentId = newDocument();
      FileDrop edited = legacyPendingFileDrop(documentId);
      edited.setLastModifiedBy(testUserHolder.get());
      payloadRepository.save(edited);
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();

      // -- ACT --
      FileDrop reused =
          payloadService.getFileDropPayloadByDocument(defaultTenant, documentId, scenario);

      // -- ASSERT --
      assertThat(reused.getApprovalStatus()).isEqualTo(Payload.PAYLOAD_APPROVAL_STATUS.PENDING);
    }

    @Test
    @DisplayName("an author without approve content changing the dropped file makes it pending")
    void given_authorEditsFileDropContent_should_makeItPending() throws Exception {
      // -- ARRANGE --
      FileDrop fileDrop = payloadService.createFileDropPayload(defaultTenant, newDocument());
      String actionId =
          injectorContractRepository.findInjectorContractByPayload(fileDrop).orElseThrow().getId();
      entityManager.flush();
      ThreatArsenalActionUpdateInput input =
          new ThreatArsenalActionUpdateInput(
              fileDrop.getName(),
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              fileDrop.getDescription(),
              null,
              null,
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
              Collections.emptyMap(),
              null,
              newDocument(),
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

      // -- ACT --
      updateAs(author, actionId, input);

      // -- ASSERT --
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }
  }

  @Nested
  @DisplayName("Approval rights removed during a session (#8410)")
  class ApprovalRightsRemoved {

    @Test
    @DisplayName(
        "once approve content is removed from their role, a user's edit is pending and they can"
            + " neither approve nor reject it")
    void given_approveContentRemovedMidSession_should_refuseDecisions() throws Exception {
      // -- ARRANGE --
      TenantRoleComposer.Composer roleComposer =
          tenantRoleComposer.forRole(
              TenantRoleFixture.getRole(
                  new HashSet<>(
                      Set.of(
                          Capability.ACCESS_THREAT_ARSENALS,
                          Capability.MANAGE_THREAT_ARSENALS,
                          Capability.APPROVE_THREAT_ARSENALS))));
      User formerApprover =
          userComposer
              .forUser(
                  UserFixture.getUser(
                      "Former", "Approver", UUID.randomUUID() + "@unittests.invalid"))
              .withGroup(
                  tenantGroupComposer
                      .forGroup(TenantGroupFixture.getGroup())
                      .withRole(roleComposer))
              .persist()
              .get();
      tenantRepository.addUserToTenant(formerApprover.getId(), Tenant.DEFAULT_TENANT_UUID);
      tenantMembershipCacheManager.evict(formerApprover.getId(), Tenant.DEFAULT_TENANT_UUID);
      Role role = roleComposer.get();
      // One authentication reused for every request, like an active browser session
      Authentication session = buildAuthenticationToken(formerApprover);
      String actionId = createAction(session);
      assertThat(approvalStatus(actionId)).isEqualTo("APPROVED");

      // -- ACT: an admin removes Approve content from the role, then the user edits --
      mvc.perform(
              put(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          new RoleInput(
                              role.getName(),
                              null,
                              Set.of(
                                  Capability.ACCESS_THREAT_ARSENALS,
                                  Capability.MANAGE_THREAT_ARSENALS)))))
          .andExpect(status().is2xxSuccessful());
      updateAs(session, actionId, update("echo after removal", "Command line payload"));

      // -- ASSERT --
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
      mvc.perform(
              post(url("/" + actionId + "/approve"))
                  .with(authentication(session))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(new ThreatArsenalApproveInput(fingerprint(actionId), null))))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(url("/" + actionId + "/reject"))
                  .with(authentication(session))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ThreatArsenalRejectInput("Not mine to reject"))))
          .andExpect(status().isForbidden());
      assertThat(approvalStatus(actionId)).isEqualTo("PENDING");
    }
  }
}
