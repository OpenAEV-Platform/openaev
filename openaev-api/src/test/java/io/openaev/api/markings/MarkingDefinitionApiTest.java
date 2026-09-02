package io.openaev.api.markings;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.markings.form.MarkingDefinitionInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD coverage for {@link MarkingDefinitionApi}.
 *
 * <p>Tenant isolation is NOT tested here — it needs {@code marking_definitions} in {@code
 * openaev.tenant.active-tables}, which is a class-level {@code @TestPropertySource} and would fork
 * the Spring context for this whole class. It lives in {@code MarkingDefinitionHttpIsolationTest}.
 *
 * <p>Every request carries an explicit {@code X-Tenant-Ids} selector. {@code create} resolves its
 * write tenant through {@code TenantWriteScopeResolver}, which refuses a scope that does not pin
 * exactly one tenant; pinning it here makes the test independent of how many tenants the mock user
 * happens to belong to.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Marking definition API tests")
class MarkingDefinitionApiTest extends IntegrationTest {

  // MarkingDefinitionApi declares its path inline in @RequestMapping rather than as a shared
  // constant, so it cannot be static-imported here.
  static final String MARKING_DEFINITION_URI = "/api/marking-definitions";
  static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingDefinitionRepository markingDefinitionRepository;

  private String tenantId;

  @BeforeEach
  void setUp() {
    markingDefinitionComposer.reset();
    tenantId = TenantContext.getCurrentTenant();
    // Keep membership in sync with the TxCtx resolver: an unauthorised selector is a 403, not a
    // silent drop.
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);
  }

  private MarkingDefinitionComposer.Composer persisted(MarkingDefinition marking) {
    return markingDefinitionComposer.forMarkingDefinition(marking).withTenantId(tenantId).persist();
  }

  private MockHttpServletRequestBuilder scoped(MockHttpServletRequestBuilder builder) {
    return builder.header(TENANT_IDS_HEADER, tenantId);
  }

  private MockHttpServletRequestBuilder jsonBody(
      MockHttpServletRequestBuilder builder, Object body) {
    return scoped(builder)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(asJsonString(body))
        .with(csrf());
  }

  // -- CREATE --

  @Nested
  @DisplayName("POST /api/marking-definitions")
  class CreateMarkingDefinition {

    @Test
    @DisplayName("Valid input creates the marking and persists every field")
    void given_validInput_should_createMarkingDefinition() throws Exception {
      // -- ARRANGE --
      MarkingDefinitionInput input =
          MarkingDefinitionFixture.createInput(
              MarkingDefinition.TYPE_PAP,
              MarkingDefinitionFixture.uniqueName(),
              MarkingDefinitionFixture.DEFAULT_ORDER,
              MarkingDefinitionFixture.DEFAULT_COLOR);

      // -- ACT --
      String response =
          mvc.perform(jsonBody(post(MARKING_DEFINITION_URI), input))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      String createdId = JsonPath.read(response, "$.marking_id");
      assertEquals(input.type(), JsonPath.read(response, "$.marking_type"));
      assertEquals(input.name(), JsonPath.read(response, "$.marking_name"));
      assertEquals(input.order(), JsonPath.read(response, "$.marking_order"));
      assertEquals(input.color(), JsonPath.read(response, "$.marking_color"));

      MarkingDefinition persisted = markingDefinitionRepository.findById(createdId).orElseThrow();
      assertEquals(input.type(), persisted.getType());
      assertEquals(input.name(), persisted.getName());
      assertEquals(input.order(), persisted.getOrder());
      assertEquals(input.color(), persisted.getColor());
      assertEquals(tenantId, persisted.getTenant().getId());
    }

    @Test
    @DisplayName("A name already taken is rejected")
    void given_duplicateName_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      String takenName = MarkingDefinitionFixture.uniqueName();
      persisted(MarkingDefinitionFixture.createMarkingDefinitionWithName(takenName));

      // -- ACT & ASSERT --
      mvc.perform(
              jsonBody(
                  post(MARKING_DEFINITION_URI),
                  MarkingDefinitionFixture.createInputWithName(takenName)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A colour that is not a hex code fails bean validation")
    void given_invalidColor_should_returnBadRequest() throws Exception {
      // -- ARRANGE & ACT & ASSERT --
      mvc.perform(
              jsonBody(
                  post(MARKING_DEFINITION_URI),
                  MarkingDefinitionFixture.createInputWithColor(
                      MarkingDefinitionFixture.INVALID_COLOR)))
          .andExpect(status().isBadRequest());
    }
  }

  // -- READ --

  @Nested
  @DisplayName("GET /api/marking-definitions/{markingId}")
  class GetMarkingDefinitionById {

    @Test
    @DisplayName("An existing id returns the marking")
    void given_existingId_should_returnMarkingDefinition() throws Exception {
      // -- ARRANGE --
      MarkingDefinition marking =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();

      // -- ACT --
      String response =
          mvc.perform(scoped(get(MARKING_DEFINITION_URI + "/" + marking.getId())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(marking.getId(), JsonPath.read(response, "$.marking_id"));
      assertEquals(marking.getName(), JsonPath.read(response, "$.marking_name"));
      assertEquals(marking.getType(), JsonPath.read(response, "$.marking_type"));
      assertEquals(marking.getOrder(), JsonPath.read(response, "$.marking_order"));
    }

    @Test
    @DisplayName("An unknown id is not found")
    void given_unknownId_should_returnNotFound() throws Exception {
      // -- ARRANGE & ACT & ASSERT --
      mvc.perform(scoped(get(MARKING_DEFINITION_URI + "/does-not-exist")))
          .andExpect(status().isNotFound());
    }
  }

  // -- SEARCH --

  @Nested
  @DisplayName("POST /api/marking-definitions/search")
  class SearchMarkingDefinitions {

    @Test
    @DisplayName("A text search returns only the matching marking")
    void given_textSearch_should_returnMatchingMarking() throws Exception {
      // -- ARRANGE --
      // The migration seeds nine defaults per tenant, so assert on a needle only this fixture
      // carries rather than on a total count.
      MarkingDefinition needle =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();
      MarkingDefinition other =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();

      // -- ACT --
      String response =
          mvc.perform(
                  jsonBody(
                      post(MARKING_DEFINITION_URI + "/search"),
                      PaginationFixture.simpleTextSearch(needle.getName())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(1, (int) JsonPath.read(response, "$.totalElements"));
      assertEquals(needle.getId(), JsonPath.read(response, "$.content[0].marking_id"));
      assertFalse(
          response.contains(other.getId()), "a non-matching marking must not appear in results");
    }

    @Test
    @DisplayName("A page smaller than the result set paginates")
    void given_pageSizeSmallerThanResults_should_paginate() throws Exception {
      // -- ARRANGE --
      // A shared token scopes the search to this fixture group, so the nine seeded defaults (and
      // anything another test left behind) cannot perturb the counts.
      String token = MarkingDefinitionFixture.uniqueSearchToken();
      for (int i = 0; i < 3; i++) {
        persisted(MarkingDefinitionFixture.createMarkingDefinitionWithName(token + ":LEVEL" + i));
      }

      // -- ACT --
      String response =
          mvc.perform(
                  jsonBody(
                      post(MARKING_DEFINITION_URI + "/search"),
                      PaginationFixture.getDefault().textSearch(token).size(2).build()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(3, (int) JsonPath.read(response, "$.totalElements"));
      assertEquals(2, (int) JsonPath.read(response, "$.totalPages"));
      assertEquals(2, ((java.util.List<?>) JsonPath.read(response, "$.content")).size());
    }

    @Test
    @DisplayName("A search matching nothing returns an empty page")
    void given_noMatch_should_returnEmptyPage() throws Exception {
      // -- ARRANGE & ACT --
      String response =
          mvc.perform(
                  jsonBody(
                      post(MARKING_DEFINITION_URI + "/search"),
                      PaginationFixture.simpleTextSearch(
                          MarkingDefinitionFixture.uniqueSearchToken())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(0, (int) JsonPath.read(response, "$.totalElements"));
    }
  }

  // -- UPDATE --

  @Nested
  @DisplayName("PUT /api/marking-definitions/{markingId}")
  class UpdateMarkingDefinition {

    @Test
    @DisplayName("An existing marking is updated and the new values persist")
    void given_existingMarking_should_updateSuccessfully() throws Exception {
      // -- ARRANGE --
      MarkingDefinition marking =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();
      MarkingDefinitionInput update =
          MarkingDefinitionFixture.createInput(
              MarkingDefinition.TYPE_PAP,
              MarkingDefinitionFixture.uniqueName(),
              99,
              MarkingDefinitionFixture.ALTERNATE_COLOR);

      // -- ACT --
      String response =
          mvc.perform(jsonBody(put(MARKING_DEFINITION_URI + "/" + marking.getId()), update))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(marking.getId(), JsonPath.read(response, "$.marking_id"));
      assertEquals(update.name(), JsonPath.read(response, "$.marking_name"));
      assertEquals(update.type(), JsonPath.read(response, "$.marking_type"));
      assertEquals(update.order(), JsonPath.read(response, "$.marking_order"));
      assertEquals(update.color(), JsonPath.read(response, "$.marking_color"));

      MarkingDefinition reloaded =
          markingDefinitionRepository.findById(marking.getId()).orElseThrow();
      assertEquals(update.name(), reloaded.getName());
      assertEquals(update.order(), reloaded.getOrder());
    }

    @Test
    @DisplayName("Renaming onto another marking's name is rejected")
    void given_collidingName_should_returnBadRequest() throws Exception {
      // -- ARRANGE --
      MarkingDefinition first =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();
      MarkingDefinition second =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();

      // -- ACT & ASSERT --
      mvc.perform(
              jsonBody(
                  put(MARKING_DEFINITION_URI + "/" + second.getId()),
                  MarkingDefinitionFixture.createInputWithName(first.getName())))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Keeping its own name is not treated as a collision")
    void given_sameName_should_updateSuccessfully() throws Exception {
      // -- ARRANGE --
      MarkingDefinition marking =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();
      MarkingDefinitionInput update =
          MarkingDefinitionFixture.createInput(
              marking.getType(), marking.getName(), 77, MarkingDefinitionFixture.ALTERNATE_COLOR);

      // -- ACT & ASSERT --
      mvc.perform(jsonBody(put(MARKING_DEFINITION_URI + "/" + marking.getId()), update))
          .andExpect(status().isOk());
      assertEquals(
          77, markingDefinitionRepository.findById(marking.getId()).orElseThrow().getOrder());
    }

    @Test
    @DisplayName("Updating an unknown id is not found")
    void given_unknownId_should_returnNotFound() throws Exception {
      // -- ARRANGE & ACT & ASSERT --
      mvc.perform(
              jsonBody(
                  put(MARKING_DEFINITION_URI + "/does-not-exist"),
                  MarkingDefinitionFixture.createDefaultInput()))
          .andExpect(status().isNotFound());
    }
  }

  // -- DELETE --

  @Nested
  @DisplayName("DELETE /api/marking-definitions/{markingId}")
  class DeleteMarkingDefinition {

    @Test
    @DisplayName("An existing marking is deleted and then no longer readable")
    void given_existingMarking_should_deleteSuccessfully() throws Exception {
      // -- ARRANGE --
      MarkingDefinition marking =
          persisted(MarkingDefinitionFixture.createDefaultMarkingDefinition()).get();

      // -- ACT --
      mvc.perform(scoped(delete(MARKING_DEFINITION_URI + "/" + marking.getId())).with(csrf()))
          .andExpect(status().isNoContent());

      // -- ASSERT --
      mvc.perform(scoped(get(MARKING_DEFINITION_URI + "/" + marking.getId())))
          .andExpect(status().isNotFound());
      assertTrue(markingDefinitionRepository.findById(marking.getId()).isEmpty());
    }

    @Test
    @DisplayName("Deleting an unknown id is not found")
    void given_unknownId_should_returnNotFound() throws Exception {
      // -- ARRANGE & ACT & ASSERT --
      mvc.perform(scoped(delete(MARKING_DEFINITION_URI + "/does-not-exist")).with(csrf()))
          .andExpect(status().isNotFound());
    }
  }
}
