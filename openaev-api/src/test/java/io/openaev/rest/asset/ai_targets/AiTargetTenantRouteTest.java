package io.openaev.rest.asset.ai_targets;

import static io.openaev.rest.asset.ai_targets.AiTargetApi.TENANT_AI_TARGET_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AiTargetFixture.createDefaultAiTargetInput;
import static io.openaev.utils.fixtures.PaginationFixture.simpleTextSearch;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins that every AI target call the web client makes is served on the {@code
 * /api/tenants/{tenantId}/} route and works in that tenant. The caller belongs to the default
 * tenant and to a tenant B, so only the route tells the tenant apart.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("AI target endpoints on the tenant route")
class AiTargetTenantRouteTest extends IntegrationTest {

  private static final String AI_TARGET_BY_ID_URI = TENANT_AI_TARGET_URI + "/{aiTargetId}";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void createTenantB() throws Exception {
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("ai-target-route-b").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Every call of the web client")
  class WebClientCalls {

    @Test
    @DisplayName("A create is stored in the route's tenant")
    void given_tenantRoute_should_createTheAiTargetInThatTenant() throws Exception {
      // Arrange
      AiTargetInput input = createDefaultAiTargetInput("ai-target-route-create");

      // Act
      String aiTargetId = create(input);

      // Assert
      assertEquals(tenantB, storedTenantOf(aiTargetId), "the AI target lives in tenant B");
    }

    @Test
    @DisplayName("The list, the read by id, the search and both options reads return it")
    void given_tenantRoute_should_serveEveryReadOfTheAiTarget() throws Exception {
      // Arrange
      AiTargetInput input = createDefaultAiTargetInput("ai-target-route-read");
      String aiTargetId = create(input);

      // Act & Assert
      mvc.perform(get(TENANT_AI_TARGET_URI, tenantB))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].asset_id", hasItem(aiTargetId)));
      mvc.perform(get(AI_TARGET_BY_ID_URI, tenantB, aiTargetId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_id").value(aiTargetId));
      mvc.perform(
              post(TENANT_AI_TARGET_URI + "/search", tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(simpleTextSearch(input.getName())))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[*].asset_id", hasItem(aiTargetId)));
      mvc.perform(
              get(TENANT_AI_TARGET_URI + "/options", tenantB).param("searchText", input.getName()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].id", hasItem(aiTargetId)));
      mvc.perform(
              post(TENANT_AI_TARGET_URI + "/options", tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of(aiTargetId)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].id", hasItem(aiTargetId)));
    }

    @Test
    @DisplayName("An update and a delete apply to the AI target")
    void given_tenantRoute_should_updateThenDeleteTheAiTarget() throws Exception {
      // Arrange
      String aiTargetId = create(createDefaultAiTargetInput("ai-target-route-write"));
      AiTargetInput update = createDefaultAiTargetInput("ai-target-route-renamed");

      // Act & Assert
      mvc.perform(
              put(AI_TARGET_BY_ID_URI, tenantB, aiTargetId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(update))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_name").value(update.getName()));
      mvc.perform(delete(AI_TARGET_BY_ID_URI, tenantB, aiTargetId).with(csrf()))
          .andExpect(status().isOk());
      entityManager.flush();
      assertNull(storedTenantOf(aiTargetId), "the AI target is deleted");
    }
  }

  private String create(AiTargetInput input) throws Exception {
    String body =
        mvc.perform(
                post(TENANT_AI_TARGET_URI, tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    entityManager.flush();
    return JsonPath.read(body, "$.asset_id");
  }

  /**
   * Reads on the current connection, past the statement inspector, so that the row is seen whatever
   * scope the request left on the test transaction.
   */
  private String storedTenantOf(String aiTargetId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement("SELECT tenant_id FROM assets WHERE asset_id = ?")) {
                stmt.setString(1, aiTargetId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
