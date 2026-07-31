package io.openaev.rest.finding;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ShutdownService;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.repository.FindingTriageHistoryRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.LogService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual verification of the FindingTriage feature: valid/invalid transitions, RBAC (non-admin
 * cannot revert, non-capability user gets 403), bulk partial-failure behavior, and GET history.
 * Re-detection auto-reset (both injector-path and agent-path hooks) has been removed pending a
 * real cross-run identity key - see {@code FindingTriageService}'s class javadoc.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class FindingTriageApiTest extends IntegrationTest {

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingTriageRepository findingTriageRepository;
  @Autowired private FindingTriageHistoryRepository findingTriageHistoryRepository;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private AuditLogProperties auditLogProperties;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private ShutdownService shutdownService;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setUp() {
    reset(auditLogger, auditLogProperties, logService, shutdownService);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doNothing().when(shutdownService).initiateShutdown();
    findingComposer.reset();
    injectComposer.reset();
  }

  private InjectComposer.Composer createInject() {
    return injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
  }

  private Finding createFinding(InjectComposer.Composer injectWrapper) {
    return findingComposer
        .forFinding(createDefaultTextFindingWithRandomValue())
        .withInject(injectWrapper)
        .persist()
        .get();
  }

  private JsonNode triageBody(String status, String justification) {
    return instance.objectNode().put("status", status).put("justification", justification);
  }

  @Nested
  @DisplayName("Valid transitions")
  class ValidTransitions {

    @Test
    @DisplayName("UNTRIAGED -> CONFIRMED with MANAGE_FINDING_TRIAGE -> 200 OK, status updated")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE})
    void given_untriagedFinding_when_confirming_should_return200AndUpdateStatus() throws Exception {
      Finding finding = createFinding(createInject());

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody("CONFIRMED", "Confirmed after manual review of the evidence"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_triage_status").value("CONFIRMED"));

      Optional<FindingTriage> triage = findingTriageRepository.findByFinding_Id(finding.getId());
      assertThat(triage).isPresent();
      assertThat(triage.get().getStatus()).isEqualTo(FindingTriageStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED -> FALSE_POSITIVE -> 200 OK (re-review outcome change)")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE})
    void given_confirmedFinding_when_markingFalsePositive_should_return200() throws Exception {
      Finding finding = createFinding(createInject());
      mvc.perform(
          patch("/api/findings/{id}/triage", finding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("CONFIRMED", "Confirmed after manual review of the evidence"))));

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody(
                              "FALSE_POSITIVE", "Re-review shows this is a false positive"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_triage_status").value("FALSE_POSITIVE"));
    }
  }

  @Nested
  @DisplayName("Invalid transitions")
  class InvalidTransitions {

    @Test
    @DisplayName("UNTRIAGED -> RISK_ACCEPTED directly -> 400 Bad Request")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE})
    void given_untriagedFinding_when_jumpingToRiskAccepted_should_return400() throws Exception {
      Finding finding = createFinding(createInject());

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody("RISK_ACCEPTED", "Skipping straight to risk accepted"))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Same-status transition (UNTRIAGED -> UNTRIAGED) -> 400 Bad Request")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE}, isAdmin = true)
    void given_untriagedFinding_when_settingSameStatus_should_return400() throws Exception {
      Finding finding = createFinding(createInject());

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody("UNTRIAGED", "Already untriaged, no-op requested"))))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("RBAC")
  class Rbac {

    @Test
    @DisplayName("User without MANAGE_FINDING_TRIAGE -> 403 Forbidden")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDINGS})
    void given_userWithoutTriageCapability_when_triaging_should_return403() throws Exception {
      Finding finding = createFinding(createInject());

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(triageBody("CONFIRMED", "Attempting to confirm"))))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Non-admin with MANAGE_FINDING_TRIAGE reverting to UNTRIAGED -> 403 Forbidden")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE}, isAdmin = false)
    void given_nonAdminUser_when_revertingToUntriaged_should_return403() throws Exception {
      Finding finding = createFinding(createInject());
      mvc.perform(
          patch("/api/findings/{id}/triage", finding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("CONFIRMED", "Confirmed after manual review of the evidence"))));

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody("UNTRIAGED", "Trying to revert without admin rights"))))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin with MANAGE_FINDING_TRIAGE reverting to UNTRIAGED -> 200 OK")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE}, isAdmin = true)
    void given_adminUser_when_revertingToUntriaged_should_return200() throws Exception {
      Finding finding = createFinding(createInject());
      mvc.perform(
          patch("/api/findings/{id}/triage", finding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("CONFIRMED", "Confirmed after manual review of the evidence"))));

      mvc.perform(
              patch("/api/findings/{id}/triage", finding.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          triageBody("UNTRIAGED", "Reverting per admin decision"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_triage_status").value("UNTRIAGED"));
    }
  }

  @Nested
  @DisplayName("Bulk")
  class Bulk {

    @Test
    @DisplayName("Bulk transition: one valid + one invalid -> partial success, batch not aborted")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE})
    void given_mixOfValidAndInvalidFindings_when_bulkTriaging_should_returnPartialResults()
        throws Exception {
      InjectComposer.Composer inject = createInject();
      Finding validFinding = createFinding(inject);
      Finding otherFinding = createFinding(inject);
      // Push otherFinding straight to FALSE_POSITIVE so the same bulk target (CONFIRMED) is an
      // invalid transition for it (FALSE_POSITIVE has no outgoing transition except revert).
      mvc.perform(
          patch("/api/findings/{id}/triage", otherFinding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("FALSE_POSITIVE", "Marking as false positive upfront"))));

      com.fasterxml.jackson.databind.node.ObjectNode body =
          instance
              .objectNode()
              .put("status", "CONFIRMED")
              .put("justification", "Bulk confirming after triage session");
      body.putArray("finding_ids").add(validFinding.getId()).add(otherFinding.getId());

      mvc.perform(
              patch("/api/findings/triage/bulk")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.finding_id == '" + validFinding.getId() + "')].success")
              .value(true))
          .andExpect(
              jsonPath("$[?(@.finding_id == '" + otherFinding.getId() + "')].success").value(false));

      assertThat(
              findingTriageRepository
                  .findByFinding_Id(validFinding.getId())
                  .map(FindingTriage::getStatus))
          .contains(FindingTriageStatus.CONFIRMED);
      assertThat(
              findingTriageRepository
                  .findByFinding_Id(otherFinding.getId())
                  .map(FindingTriage::getStatus))
          .contains(FindingTriageStatus.FALSE_POSITIVE);
    }
  }

  @Nested
  @DisplayName("History")
  class History {

    @Test
    @DisplayName("GET history returns transitions in chronological order")
    @WithMockUser(withCapabilities = {Capability.MANAGE_FINDING_TRIAGE})
    void given_transitionedFinding_when_gettingHistory_should_returnChronologicalOrder()
        throws Exception {
      Finding finding = createFinding(createInject());
      mvc.perform(
          patch("/api/findings/{id}/triage", finding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("CONFIRMED", "Confirmed after manual review of the evidence"))));
      mvc.perform(
          patch("/api/findings/{id}/triage", finding.getId())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  mapper.writeValueAsString(
                      triageBody("RISK_ACCEPTED", "Risk accepted by security team"))));

      mvc.perform(get("/api/findings/{id}/triage/history", finding.getId()).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
          .andExpect(jsonPath("$[0].finding_triage_history_to_status").value("CONFIRMED"))
          .andExpect(jsonPath("$[1].finding_triage_history_to_status").value("RISK_ACCEPTED"));
    }
  }
}
