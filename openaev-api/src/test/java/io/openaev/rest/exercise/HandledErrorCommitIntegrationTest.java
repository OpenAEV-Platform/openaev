package io.openaev.rest.exercise;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.StepFixture.getDefaultStepTemplate;
import static io.openaev.utils.fixtures.WorkflowFixture.getDefaultWorkflowTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.execution.UnexecutableInjectException;
import io.openaev.executors.Executor;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.DirectInjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * #8115: a handled error must not die at commit. A failure thrown out of another bean's
 * {@code @Transactional} method marks the caller's transaction rollback-only even when the caller
 * catches it, so a catch that carries on turns its own answer into an {@code
 * UnexpectedRollbackException} at commit.
 *
 * <p>Deliberately NOT {@code @Transactional}: the transaction under test must be the outermost one
 * and commit, which is where the defect shows. Rows are seeded and cleaned in committed
 * transactions.
 */
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("Handled errors that used to die at commit (#8115)")
class HandledErrorCommitIntegrationTest extends ByIdRequestScopeProductionTablesTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @MockitoSpyBean private Executor executor;
  @MockitoSpyBean private StepService stepService;

  @Autowired private InjectService injectService;
  @Autowired private WorkflowService workflowService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private DataSource dataSource;

  private final List<String> seededWorkflows = new ArrayList<>();

  @AfterEach
  void cleanupWorkflows() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    seededWorkflows.forEach(id -> jdbc.update("DELETE FROM steps WHERE step_workflow_id = ?", id));
    seededWorkflows.forEach(id -> jdbc.update("DELETE FROM workflows WHERE workflow_id = ?", id));
    seededWorkflows.clear();
  }

  @Nested
  @DisplayName("Direct execution of an inject in a simulation")
  class DirectInjectExecution {

    @Test
    @DisplayName(
        "given_failureThrownOutOfAnotherTransactionalBean_should_answerThatFailureAndWriteNothing")
    void given_failureThrownOutOfAnotherTransactionalBean_should_answerThatFailureAndWriteNothing()
        throws Exception {
      // -- ARRANGE --
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);
      // The execution fails inside another bean's transactional method, which marks the request's
      // transaction rollback-only: here an inject lookup that does not find its inject.
      Mockito.doAnswer(
              invocation -> {
                injectService.existsInScope(
                    TxCtx.forTenant(memberTenant), UUID.randomUUID().toString());
                return null;
              })
          .when(executor)
          .directExecute(Mockito.any());

      // -- ACT --
      MockHttpServletResponse response = executeDirectInject(simulationId, contractId);

      // -- ASSERT --
      assertThat(response.getStatus())
          .as("the failure itself, not an UnexpectedRollbackException at commit (500)")
          .isEqualTo(404);
      assertThat(injectCount(simulationId)).as("nothing half-written is committed").isZero();
    }

    @Test
    @DisplayName("given_failureThatLeavesTheTransactionSound_should_recordItOnTheInject")
    void given_failureThatLeavesTheTransactionSound_should_recordItOnTheInject() throws Exception {
      // -- ARRANGE --
      String simulationId = seedSimulation(memberTenant);
      String contractId = seedInjectorContract(memberTenant);
      Mockito.doThrow(new UnexecutableInjectException("No started connector instance"))
          .when(executor)
          .directExecute(Mockito.any());

      // -- ACT --
      MockHttpServletResponse response = executeDirectInject(simulationId, contractId);

      // -- ASSERT --
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(MAPPER.readTree(response.getContentAsString()).get("status_name").asText())
          .isEqualTo("ERROR");
      assertThat(injectCount(simulationId)).as("the failed inject is kept").isEqualTo(1);
    }

    private MockHttpServletResponse executeDirectInject(String simulationId, String contractId)
        throws Exception {
      DirectInjectInput input = new DirectInjectInput();
      input.setTitle("handled error at commit");
      input.setInjectorContract(contractId);
      input.setContent(MAPPER.createObjectNode());
      input.setUserIds(List.of());
      return mvc.perform(
              multipart(tenantRoute(memberTenant).simulation(simulationId) + "/inject")
                  .file(
                      new MockMultipartFile(
                          "input",
                          "",
                          MediaType.APPLICATION_JSON_VALUE,
                          asJsonString(input).getBytes()))
                  .file(
                      new MockMultipartFile(
                          "file", "direct.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andReturn()
          .getResponse();
    }
  }

  @Nested
  @DisplayName("Full reset of a scenario's chaining steps")
  class ScenarioStepsReset {

    @Test
    @DisplayName(
        "given_stepDeletionFailingInsideStepService_should_failWithThatErrorAndDeleteNothing")
    void given_stepDeletionFailingInsideStepService_should_failWithThatErrorAndDeleteNothing() {
      // -- ARRANGE --
      String scenarioId = seedScenario(memberTenant);
      String workflowId = seedWorkflowWithOneStep(scenarioId);
      // The step deletion fails inside a transactional method of another bean, which marks the
      // reset's transaction rollback-only: here an inject lookup that does not find its inject.
      Mockito.doAnswer(
              invocation -> {
                injectService.existsInScope(
                    TxCtx.forTenant(memberTenant), UUID.randomUUID().toString());
                return null;
              })
          .when(stepService)
          .deleteStepTemplate(Mockito.anyString());

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  tenantTx.execute(
                      TxCtx.forTenant(memberTenant),
                      () -> workflowService.deleteAllScenarioSteps(scenarioId)))
          .as("the failure itself, not an UnexpectedRollbackException at commit")
          .isInstanceOf(ElementNotFoundException.class);
      assertThat(stepCount(workflowId)).as("all or nothing").isEqualTo(1);
    }

    @Test
    @DisplayName("given_editableLogicMap_should_deleteEveryStep")
    void given_editableLogicMap_should_deleteEveryStep() {
      // -- ARRANGE --
      String scenarioId = seedScenario(memberTenant);
      String workflowId = seedWorkflowWithOneStep(scenarioId);

      // -- ACT --
      tenantTx.execute(
          TxCtx.forTenant(memberTenant), () -> workflowService.deleteAllScenarioSteps(scenarioId));

      // -- ASSERT --
      assertThat(stepCount(workflowId)).isZero();
    }
  }

  // -- HELPERS --

  private String seedWorkflowWithOneStep(String scenarioId) {
    String id =
        inTenantScope(
            memberTenant,
            () -> {
              Workflow template = getDefaultWorkflowTemplate();
              template.setTenant(new Tenant(memberTenant));
              template.setScenario(scenarioRepository.getReferenceById(scenarioId));
              Workflow saved = workflowRepository.save(template);
              Step step = getDefaultStepTemplate();
              step.setTenant(new Tenant(memberTenant));
              step.setWorkflow(saved);
              stepRepository.save(step);
              return saved.getId();
            });
    seededWorkflows.add(id);
    return id;
  }

  private int stepCount(String workflowId) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT count(*) FROM steps WHERE step_workflow_id = ?", Integer.class, workflowId);
  }
}
