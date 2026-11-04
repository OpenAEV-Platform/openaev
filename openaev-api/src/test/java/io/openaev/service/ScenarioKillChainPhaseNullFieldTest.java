package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.openaev.rest.scenario.response.ScenarioOutput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.KillChainPhaseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code ScenarioRepository#getScenarioByIdAndTenantId} aggregates a scenario's kill chain phases
 * with {@code json_agg(DISTINCT kcp.*) FILTER (WHERE kcp IS NOT NULL)}. PostgreSQL's row {@code IS
 * NOT NULL} is true only when every field of the row is non-null, so a phase with a null {@code
 * phase_description} or {@code phase_stix_id} (both nullable columns) is silently dropped from the
 * scenario detail, even though every join on the way to it is an INNER JOIN and the phase is
 * genuinely linked.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class ScenarioKillChainPhaseNullFieldTest extends IntegrationTest {

  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private KillChainPhaseRepository killChainPhaseRepository;

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName(
      "given a kill chain phase with a null description and stix id, should still appear in the scenario detail")
  void given_killChainPhaseWithNullDescriptionAndStixId_should_appearInScenarioDetail() {
    // Arrange: a kill chain phase whose description and stix id are left null, linked to an
    // attack pattern, itself linked to the injector contract of an inject of the scenario.
    KillChainPhase phase =
        KillChainPhaseFixture.getKillChainPhase(
            "phase-null-field-" + UUID.randomUUID(), 1L, Tenant.DEFAULT_TENANT_UUID);
    assertThat(phase.getDescription()).isNull();
    assertThat(phase.getStixId()).isNull();
    killChainPhaseRepository.save(phase);

    AttackPattern attackPattern =
        AttackPatternFixture.createDefaultAttackPattern(Tenant.DEFAULT_TENANT_UUID);
    attackPattern.setKillChainPhases(List.of(phase));

    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    injectorContractComposer
        .forInjectorContract(injectorContract)
        .withAttackPattern(attackPatternComposer.forAttackPattern(attackPattern))
        .persist();

    Scenario scenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();

    Inject inject =
        InjectFixture.createInject(injectorContract, "kill-chain-phase-null-field-inject");
    inject.setScenario(scenario);
    injectComposer.forInject(inject).persist();

    entityManager.flush();
    entityManager.clear();

    // Act
    ScenarioOutput scenarioOutput =
        scenarioService.getScenarioById(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), scenario.getId());

    // Assert
    assertThat(scenarioOutput.getKillChainPhases())
        .extracting(KillChainPhaseOutput::getId)
        .contains(phase.getId());
  }
}
