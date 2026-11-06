package io.openaev.rest.exercise;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.AmbientTenantBridge;
import io.openaev.context.TenantContext;
import io.openaev.rest.inject.form.DirectInjectInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The direct inject execution attributes the inject it creates to its simulation's tenant on the
 * entity itself. {@link AmbientTenantBridge} is replaced by a pass-through here, so the ambient
 * tenant of the non-prefixed route (the default one) is what a write that relied on it would be
 * stamped with, and the tenant the row carries can only come from the inject.
 */
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("A directly executed inject carries its simulation's tenant, not the ambient one")
class DirectInjectExecutionTenantTest extends ByIdRequestScopeProductionTablesTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @MockitoBean private AmbientTenantBridge ambientTenantBridge;

  @BeforeEach
  void bridgeThatChangesNothing() {
    Mockito.when(ambientTenantBridge.callInTenant(Mockito.any(), Mockito.any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
  }

  @Test
  @DisplayName("given_simulationOfTheSelectedTenantAndNoBridge_should_writeTheInjectInItsTenant")
  void given_simulationOfTheSelectedTenantAndNoBridge_should_writeTheInjectInItsTenant()
      throws Exception {
    // Arrange: the selector pins the simulation's tenant, the ambient tenant stays the default one.
    String simulationId = seedSimulation(memberTenant);
    String contractId = seedInjectorContract(memberTenant);
    Route route = headerRoute(memberTenant);
    DirectInjectInput input = new DirectInjectInput();
    input.setTitle("direct inject tenant");
    input.setInjectorContract(contractId);
    input.setContent(MAPPER.createObjectNode());
    input.setUserIds(List.of());

    // Act
    TenantContext.clearCurrentTenant();
    int status =
        mvc.perform(
                multipart(route.simulation(simulationId) + "/inject")
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
                    .header(TENANT_IDS_HEADER, route.headerTenant())
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus();

    // Assert
    assertThat(status).isEqualTo(200);
    assertThat(injectTenants(simulationId)).containsExactly(memberTenant);
  }
}
