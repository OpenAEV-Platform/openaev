package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.api.autonomous.dto.CapabilityReport;
import io.openaev.api.autonomous.dto.CapabilityResolution;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.inject.service.InjectAssistantService;
import io.openaev.service.autonomous.CapabilityResolverService;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.VulnerabilityFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.VulnerabilityComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wherever the platform picks actions on its own, it follows the pickers' rule: only approved
 * payloads, plus payload-less built-ins (Task 4).
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Automatic selection only picks approved payloads (Task 4)")
class AutomaticSelectionApprovalTest extends IntegrationTest {

  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private InjectAssistantService injectAssistantService;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private CapabilityResolverService capabilityResolverService;

  @BeforeEach
  void beforeEach() {
    injectorContractComposer.reset();
    payloadComposer.reset();
    attackPatternComposer.reset();
    vulnerabilityComposer.reset();
  }

  private Payload payload(Payload.PAYLOAD_APPROVAL_STATUS status) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setApprovalStatus(status);
    return payload;
  }

  private InjectorContractComposer.Composer contract(Payload payload) {
    InjectorContractComposer.Composer composer =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(InjectorFixture.createDefaultPayloadInjector());
    return payload == null ? composer : composer.withPayload(payloadComposer.forPayload(payload));
  }

  private AttackPatternComposer.Composer attackPattern(String externalId) {
    return attackPatternComposer
        .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId(externalId))
        .persist();
  }

  private static String uniqueTechnique() {
    // Unique per test: the assistant matches technique ids by prefix
    return "T9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
  }

  private static Set<String> contractIds(List<Inject> injects) {
    return injects.stream()
        .map(inject -> inject.getInjectorContract().orElseThrow().getId())
        .collect(Collectors.toSet());
  }

  @Nested
  @DisplayName("Inject assistant and security coverage (US4.2)")
  class InjectGeneration {

    @Test
    @DisplayName("given_approvedAndPendingActionsForTechnique_should_pickOnlyApproved")
    void given_approvedAndPendingActionsForTechnique_should_pickOnlyApproved() {
      // -- ARRANGE --
      String technique = uniqueTechnique();
      AttackPatternComposer.Composer pattern = attackPattern(technique);
      InjectorContract approved =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED))
              .withAttackPattern(pattern)
              .persist()
              .get();
      InjectorContract pending =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.PENDING))
              .withAttackPattern(pattern)
              .persist()
              .get();
      InjectorContract placeholder = injectorContractFixture.getWellKnownSingleManualContract();

      // -- ACT --
      List<Inject> injects =
          injectAssistantService.generateInjectsByAttackPatternsWithoutAssetGroups(
              new Scenario(), Set.of(pattern.get()), 5, placeholder);

      // -- ASSERT --
      assertThat(contractIds(injects)).containsExactly(approved.getId());
      assertThat(contractIds(injects)).doesNotContain(pending.getId());
    }

    @Test
    @DisplayName("given_onlyPendingActionForTechnique_should_createManualPlaceholder")
    void given_onlyPendingActionForTechnique_should_createManualPlaceholder() {
      // -- ARRANGE --
      AttackPatternComposer.Composer pattern = attackPattern(uniqueTechnique());
      contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.PENDING))
          .withAttackPattern(pattern)
          .persist();
      InjectorContract placeholder = injectorContractFixture.getWellKnownSingleManualContract();

      // -- ACT --
      List<Inject> injects =
          injectAssistantService.generateInjectsByAttackPatternsWithoutAssetGroups(
              new Scenario(), Set.of(pattern.get()), 5, placeholder);

      // -- ASSERT --
      assertThat(contractIds(injects)).containsExactly(placeholder.getId());
    }

    @Test
    @DisplayName("given_vulnerabilityActions_should_returnApprovedAndPayloadLessOnly")
    void given_vulnerabilityActions_should_returnApprovedAndPayloadLessOnly() {
      // -- ARRANGE --
      String cve = VulnerabilityFixture.getRandomExternalVulnerabilityId();
      VulnerabilityComposer.Composer vulnerability =
          vulnerabilityComposer
              .forVulnerability(VulnerabilityFixture.createVulnerabilityInput(cve))
              .persist();
      InjectorContract approved =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED))
              .withVulnerability(vulnerability)
              .persist()
              .get();
      InjectorContract pending =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.PENDING))
              .withVulnerability(vulnerability)
              .persist()
              .get();
      InjectorContract payloadLess =
          contract(null).withVulnerability(vulnerability).persist().get();

      // -- ACT --
      Set<String> found =
          injectorContractRepository
              .findInjectorContractsByVulnerabilityIdIn(Set.of(cve.toLowerCase()), 5)
              .stream()
              .map(InjectorContract::getId)
              .collect(Collectors.toSet());

      // -- ASSERT --
      assertThat(found).contains(approved.getId(), payloadLess.getId());
      assertThat(found).doesNotContain(pending.getId());
    }
  }

  @Nested
  @DisplayName("AI orchestrator candidates (US4.4)")
  class OrchestratorCandidates {

    @Test
    @DisplayName("given_approvedAndPendingActionsForTechnique_should_offerOnlyApproved")
    void given_approvedAndPendingActionsForTechnique_should_offerOnlyApproved() {
      // -- ARRANGE --
      String technique = uniqueTechnique();
      AttackPatternComposer.Composer pattern = attackPattern(technique);
      InjectorContract approved =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED))
              .withAttackPattern(pattern)
              .persist()
              .get();
      InjectorContract pending =
          contract(payload(Payload.PAYLOAD_APPROVAL_STATUS.PENDING))
              .withAttackPattern(pattern)
              .persist()
              .get();
      CapabilityQueryInput query = new CapabilityQueryInput();
      query.setTechniques(List.of(technique));

      // -- ACT --
      CapabilityReport report = capabilityResolverService.resolve(query);

      // -- ASSERT --
      Set<String> offered =
          report.getResolutions().stream()
              .flatMap(resolution -> resolution.getContracts().stream())
              .map(CapabilityResolution.ResolvedContract::getInjectorContractId)
              .collect(Collectors.toSet());
      assertThat(offered).contains(approved.getId()).doesNotContain(pending.getId());
    }
  }
}
