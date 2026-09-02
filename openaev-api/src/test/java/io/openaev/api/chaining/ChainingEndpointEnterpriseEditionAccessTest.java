package io.openaev.api.chaining;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Chaining endpoints Enterprise Edition access tests")
class ChainingEndpointEnterpriseEditionAccessTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setUp() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(true);
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Condition API findById")
  void given_inactiveEnterpriseLicense_should_denyConditionFindById() throws Exception {
    assertEnterpriseEditionDenied(
        get(tenantUri(ConditionApi.TENANT_CONDITION_URI) + "/condition-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Condition API findAllByWorkflow")
  void given_inactiveEnterpriseLicense_should_denyConditionFindAllByWorkflow() throws Exception {
    assertEnterpriseEditionDenied(
        get(tenantUri(ConditionApi.TENANT_CONDITION_URI)).param("workflow_id", "workflow-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Step API findById")
  void given_inactiveEnterpriseLicense_should_denyStepFindById() throws Exception {
    assertEnterpriseEditionDenied(get(tenantUri(StepApi.TENANT_STEP_URI) + "/step-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Step API findByWorkflowId")
  void given_inactiveEnterpriseLicense_should_denyStepFindByWorkflowId() throws Exception {
    assertEnterpriseEditionDenied(
        get(tenantUri(StepApi.TENANT_STEP_URI)).param("workflow_id", "workflow-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Workflow API configuration read")
  void given_inactiveEnterpriseLicense_should_denyWorkflowConfigurationRead() throws Exception {
    assertEnterpriseEditionDenied(
        get(tenantUri(WorkflowApi.TENANT_WORKFLOW_URI) + "/workflow-id/configuration"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Workflow API valid assets read")
  void given_inactiveEnterpriseLicense_should_denyWorkflowValidAssetsRead() throws Exception {
    assertEnterpriseEditionDenied(
        get(tenantUri(WorkflowApi.TENANT_WORKFLOW_URI) + "/workflow-id/valid-assets"));
  }

  private void assertEnterpriseEditionDenied(MockHttpServletRequestBuilder request)
      throws Exception {
    mvc.perform(request)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
  }
}
