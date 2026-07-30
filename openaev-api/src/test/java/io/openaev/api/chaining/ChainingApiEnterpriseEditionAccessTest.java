package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
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
@DisplayName("Chaining APIs Enterprise Edition access tests")
class ChainingApiEnterpriseEditionAccessTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setUp() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(true);
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Condition API findById")
  void given_inactiveEnterpriseLicense_should_denyConditionFindById() {
    assertEnterpriseEditionDenied(
        get(tenantUri(ConditionApi.TENANT_CONDITION_URI) + "/condition-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Condition API findAllByWorkflow")
  void given_inactiveEnterpriseLicense_should_denyConditionFindAllByWorkflow() {
    assertEnterpriseEditionDenied(
        get(tenantUri(ConditionApi.TENANT_CONDITION_URI)).param("workflow_id", "workflow-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Step API findById")
  void given_inactiveEnterpriseLicense_should_denyStepFindById() {
    assertEnterpriseEditionDenied(get(tenantUri(StepApi.TENANT_STEP_URI) + "/step-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Step API findByWorkflowId")
  void given_inactiveEnterpriseLicense_should_denyStepFindByWorkflowId() {
    assertEnterpriseEditionDenied(
        get(tenantUri(StepApi.TENANT_STEP_URI)).param("workflow_id", "workflow-id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Workflow API configuration read")
  void given_inactiveEnterpriseLicense_should_denyWorkflowConfigurationRead() {
    assertEnterpriseEditionDenied(
        get(tenantUri(WorkflowApi.TENANT_WORKFLOW_URI) + "/workflow-id/configuration"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Given inactive enterprise license should deny Workflow API valid assets read")
  void given_inactiveEnterpriseLicense_should_denyWorkflowValidAssetsRead() {
    assertEnterpriseEditionDenied(
        get(tenantUri(WorkflowApi.TENANT_WORKFLOW_URI) + "/workflow-id/valid-assets"));
  }

  private void assertEnterpriseEditionDenied(MockHttpServletRequestBuilder request) {
    ServletException exception = assertThrows(ServletException.class, () -> mvc.perform(request));
    EnterpriseEditionException cause =
        assertInstanceOf(EnterpriseEditionException.class, exception.getCause());
    assertEquals("Enterprise Edition license required", cause.getMessage());
  }
}
