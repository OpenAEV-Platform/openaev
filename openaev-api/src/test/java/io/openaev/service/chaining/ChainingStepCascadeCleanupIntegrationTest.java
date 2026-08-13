package io.openaev.service.chaining;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end reproduction of the "ghost chaining step" bug: deleting a threat-arsenal payload (or
 * its injector contract) must wipe the authored TEMPLATE steps that reference it from every
 * scenario logic map, while RUN steps (execution history) survive.
 *
 * <p>A chaining step freezes its injector contract as a JSON snapshot inside {@code step_data} with
 * no foreign key, so the database {@code ON DELETE CASCADE} that removes regular injects never
 * touched these steps - they lingered as un-editable, un-runnable ghosts. {@link
 * ChainingStepCleanupService} is the application-level compensation wired into every contract /
 * payload delete path.
 *
 * <p>The fixture graph is flushed and detached ({@code em.clear()}) before each delete so the
 * delete runs against a clean persistence context, exactly like a real HTTP request - otherwise the
 * in-session contract-&gt;payload graph fights the delete cascade at flush time.
 */
@SpringBootTest
@TestInstance(PER_CLASS)
@Transactional
class ChainingStepCascadeCleanupIntegrationTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractService injectorContractService;
  @Autowired private PayloadService payloadService;

  private record Fixture(
      String contractId, String payloadId, String templateStepId, String runStepId) {}

  private Step contractStep(StepStatus status, String contractId) {
    return Step.builder()
        .stepAction(StepActionClass.INJECT_EXECUTION)
        .status(status)
        .data("{\"inject_injector_contract\": {\"injector_contract_id\": \"" + contractId + "\"}}")
        .build();
  }

  /**
   * Persists a payload-backed custom injector contract and a scenario workflow that references it
   * through one TEMPLATE step (authored node) and one RUN step (execution history), then detaches
   * everything so the subsequent delete runs against a clean session.
   */
  private Fixture persistContractAndScenarioSteps() {
    PayloadComposer.Composer payloadWrapper =
        payloadComposer.forPayload(PayloadFixture.createDefaultCommand());
    InjectorContractComposer.Composer contractWrapper =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
            .withInjector(InjectorFixture.createDefaultPayloadInjector())
            .withPayload(payloadWrapper);
    contractWrapper.persist();
    String contractId = contractWrapper.get().getId();
    String payloadId = payloadWrapper.get().getId();

    Step templateStep = contractStep(StepStatus.TEMPLATE, contractId);
    Step runStep = contractStep(StepStatus.RUN, contractId);
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withScenario(scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()))
        .withStep(stepComposer.forStep(templateStep))
        .withStep(stepComposer.forStep(runStep))
        .persist();

    entityManager.flush();
    entityManager.clear();
    return new Fixture(contractId, payloadId, templateStep.getId(), runStep.getId());
  }

  @Test
  @DisplayName("Deleting the injector contract wipes the TEMPLATE step but keeps the RUN step")
  void whenContractDeleted_thenTemplateStepIsWipedAndRunStepSurvives() {
    Fixture fixture = persistContractAndScenarioSteps();

    // Sanity: both steps exist before the deletion.
    Assertions.assertTrue(stepRepository.findById(fixture.templateStepId()).isPresent());
    Assertions.assertTrue(stepRepository.findById(fixture.runStepId()).isPresent());

    // WHEN: the contract is removed from the threat arsenal.
    injectorContractService.deleteInjectorContractById(fixture.contractId());
    entityManager.flush();
    entityManager.clear();

    // THEN: the authored TEMPLATE node is gone, the execution-history RUN step survives.
    Assertions.assertTrue(
        stepRepository.findById(fixture.templateStepId()).isEmpty(),
        "TEMPLATE step referencing the deleted contract must be wiped");
    Assertions.assertTrue(
        stepRepository.findById(fixture.runStepId()).isPresent(),
        "RUN step carries immutable execution history and must survive");
  }

  @Test
  @DisplayName("Deleting the payload cascades through its contract to wipe the TEMPLATE step")
  void whenPayloadDeleted_thenTemplateStepIsWiped() {
    Fixture fixture = persistContractAndScenarioSteps();

    Assertions.assertTrue(stepRepository.findById(fixture.templateStepId()).isPresent());

    // WHEN: the underlying payload is deleted - the contract id is resolved BEFORE the DB cascade
    // removes the contract row, then the chaining steps are swept.
    payloadService.delete(fixture.payloadId());
    entityManager.flush();
    entityManager.clear();

    // THEN
    Assertions.assertTrue(
        stepRepository.findById(fixture.templateStepId()).isEmpty(),
        "TEMPLATE step must be wiped when the payload behind its contract is deleted");
    Assertions.assertTrue(
        stepRepository.findById(fixture.runStepId()).isPresent(),
        "RUN step carries immutable execution history and must survive");
  }
}
