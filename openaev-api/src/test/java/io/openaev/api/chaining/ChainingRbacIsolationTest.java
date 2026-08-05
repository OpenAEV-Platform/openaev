package io.openaev.api.chaining;

import static io.openaev.api.chaining.ConditionApi.TENANT_CONDITION_URI;
import static io.openaev.api.chaining.StepApi.TENANT_STEP_URI;
import static io.openaev.api.chaining.WorkflowApi.TENANT_WORKFLOW_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Condition;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Grant;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * #6357: the chaining engine tables (workflow/step/condition) carry no tenant_id and are not
 * tenant-active, so their cross-tenant protection is not the MT-v2 SQL inspector but the {@link
 * io.openaev.aop.AccessControl} parent-permission chain: a read/write resolves the resource up to
 * its owning simulation/scenario (grant-managed, tenant-scoped) and requires a grant on it (see
 * {@code PermissionService.resolveWorkflowTarget}).
 *
 * <p>Scope of this proof, stated honestly: it is a single-tenant, grant-based RBAC test on the real
 * stack (MockMvc + real PG + real grants), the same idiom as {@code AttackPathApiRbacTest}. It does
 * not spin two tenants; it relies on the established fact that grants are tenant-scoped, so "no
 * grant on the simulation" is exactly the position of a user from another tenant. It covers the
 * user-facing HTTP vector (reads and one write), NOT the background engine vector (that is #6904's
 * output stamping). Each negative pairs with a positive that returns 200, so the 403 is proven to
 * come from the grant check and not from a broken or always-deny endpoint.
 */
@Transactional
@DisplayName(
    "chaining engine reads/writes enforce the simulation grant (tenant isolation via RBAC)")
class ChainingRbacIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ConditionComposer conditionComposer;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String workflowId;
  private String stepId;
  private String conditionId;
  private String simulationId;
  private String originalDevFeatures;

  @BeforeEach
  void setUp() {
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    openAEVConfig.setEnabledDevFeatures(PreviewFeature.INJECT_CHAINING.getValue());
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    clearFeatureCache();

    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setFrom("exercise@mail.fr");
    ExerciseComposer.Composer simulation = exerciseComposer.forExercise(exercise);
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    Workflow persisted =
        workflowComposer
            .forWorkflow(workflow)
            .withSimulation(simulation)
            .withStep(step)
            .persist()
            .get();
    step.persist();
    workflowId = persisted.getId();
    simulationId = simulation.get().getId();
    stepId = step.get().getId();

    Condition condition = ConditionFixture.getDefaultCondition(PrimitiveType.Port, "445");
    condition.setWorkflowId(workflowId);
    conditionComposer.forCondition(condition).withStep(step).persist();
    conditionId = condition.getId();
  }

  @AfterEach
  void tearDown() {
    openAEVConfig.setEnabledDevFeatures(originalDevFeatures);
    clearFeatureCache();
  }

  private void clearFeatureCache() {
    var cache = cacheManager.getCache("global");
    if (cache != null) {
      cache.clear();
    }
  }

  private void grantReadOnSimulation() {
    addGrantToCurrentUser(
        Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulationId);
  }

  private void grantWriteOnSimulation() {
    addGrantToCurrentUser(
        Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulationId);
  }

  private String workflowUri() {
    return tenantUri(TENANT_WORKFLOW_URI + "/" + workflowId + "/configuration");
  }

  private String stepUri() {
    return tenantUri(TENANT_STEP_URI + "/" + stepId);
  }

  private String conditionUri() {
    return tenantUri(TENANT_CONDITION_URI + "/" + conditionId);
  }

  // -- WORKFLOW read (resolves workflow -> simulation) --

  @Test
  @WithMockUser
  @DisplayName("workflow read: no grant on the simulation is forbidden (403)")
  void withoutGrant_workflowReadForbidden() throws Exception {
    mockMvc.perform(get(workflowUri()).with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  @DisplayName("workflow read: a READ grant on the simulation returns the resource (200)")
  void withGrant_workflowReadAllowed() throws Exception {
    grantReadOnSimulation();
    mockMvc.perform(get(workflowUri()).with(csrf())).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("workflow read: an admin bypasses the grant (200)")
  void admin_workflowReadAllowed() throws Exception {
    mockMvc.perform(get(workflowUri()).with(csrf())).andExpect(status().isOk());
  }

  // -- STEP read (resolves step -> workflow -> simulation) --

  @Test
  @WithMockUser
  @DisplayName("step read: no grant on the simulation is forbidden (403)")
  void withoutGrant_stepReadForbidden() throws Exception {
    mockMvc.perform(get(stepUri()).with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  @DisplayName("step read: a READ grant on the simulation returns the resource (200)")
  void withGrant_stepReadAllowed() throws Exception {
    grantReadOnSimulation();
    mockMvc.perform(get(stepUri()).with(csrf())).andExpect(status().isOk());
  }

  // -- CONDITION read (resolves condition -> workflow -> simulation) --

  @Test
  @WithMockUser
  @DisplayName("condition read: no grant on the simulation is forbidden (403)")
  void withoutGrant_conditionReadForbidden() throws Exception {
    mockMvc.perform(get(conditionUri()).with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  @DisplayName("condition read: a READ grant on the simulation returns the resource (200)")
  void withGrant_conditionReadAllowed() throws Exception {
    grantReadOnSimulation();
    mockMvc.perform(get(conditionUri()).with(csrf())).andExpect(status().isOk());
  }

  // -- WORKFLOW write (same resolveTarget, WRITE action -> hasWriteGrant): the mutation path is
  // gated too, not only reads. --

  @Test
  @WithMockUser
  @DisplayName("workflow write: no grant on the simulation is forbidden (403)")
  void withoutGrant_workflowWriteForbidden() throws Exception {
    mockMvc
        .perform(
            put(workflowUri())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(validConfig())))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  @DisplayName("workflow write: a WRITE grant on the simulation is allowed (200)")
  void withGrant_workflowWriteAllowed() throws Exception {
    grantWriteOnSimulation();
    mockMvc
        .perform(
            put(workflowUri())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(validConfig())))
        .andExpect(status().isOk());
  }

  private WorkflowConfigurationInput validConfig() {
    return WorkflowConfigurationInput.builder()
        .rateLimitEnabled(false)
        .safeModeEnabled(true)
        .build();
  }
}
