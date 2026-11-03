package io.openaev.api.marking_definition;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createDefaultMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@DisplayName("Marking definition API")
class MarkingDefinitionApiTest extends IntegrationTest {

  private static final String URI = "/api/tenants/{tenantId}/marking_definitions";

  @Autowired private MockMvc mvc;
  @Autowired private MarkingDefinitionRepository repository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_MARKING_DEFINITION,
        Capability.DELETE_MARKING_DEFINITION,
        Capability.ACCESS_MARKING_DEFINITION
      },
      // These tests target Tenant.DEFAULT_TENANT_UUID directly (no tenant of their own), so the
      // mock user needs real membership there for TenantInterceptor to let the request through.
      autoJoinDefaultTenant = true)
  @DisplayName("CRUD operations")
  class CrudOperations {

    @Test
    @DisplayName("given_validInput_should_createMarkingDefinition")
    void given_validInput_should_createMarkingDefinition() throws Exception {
      // Arrange
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:BLUE",
            "marking_definition_color": "#2196F3",
            "marking_definition_order": 6
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.marking_definition_type").value("TLP"))
          .andExpect(jsonPath("$.marking_definition_definition").value("TLP:BLUE"))
          .andExpect(jsonPath("$.marking_definition_color").value("#2196F3"))
          .andExpect(jsonPath("$.marking_definition_order").value(6))
          .andExpect(jsonPath("$.marking_definition_protected").value(false));
    }

    @Test
    @DisplayName("given_negativeOrder_should_rejectCreation")
    void given_negativeOrder_should_rejectCreation() throws Exception {
      // Arrange
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:INVALID",
            "marking_definition_color": "#2196F3",
            "marking_definition_order": -1
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_blankColor_should_rejectCreation")
    void given_blankColor_should_rejectCreation() throws Exception {
      // Arrange
      long countBefore = repository.count();
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:BLANK-COLOR",
            "marking_definition_color": "",
            "marking_definition_order": 7
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("given_whitespaceColor_should_rejectCreation")
    void given_whitespaceColor_should_rejectCreation() throws Exception {
      // Arrange
      long countBefore = repository.count();
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:WHITESPACE-COLOR",
            "marking_definition_color": "   ",
            "marking_definition_order": 7
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("given_missingColor_should_rejectCreation")
    void given_missingColor_should_rejectCreation() throws Exception {
      // Arrange
      long countBefore = repository.count();
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:MISSING-COLOR",
            "marking_definition_order": 8
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("given_malformedColor_should_rejectCreation")
    void given_malformedColor_should_rejectCreation() throws Exception {
      // Arrange
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:MALFORMED-COLOR",
            "marking_definition_color": "not-a-hex-color",
            "marking_definition_order": 7
          }
          """;

      // Act & Assert
      mvc.perform(
              post(URI, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_blankColor_should_rejectUpdate")
    void given_blankColor_should_rejectUpdate() throws Exception {
      // Arrange
      MarkingDefinition existing =
          createPersistedMarkingDefinition(
              Tenant.DEFAULT_TENANT_UUID,
              "TLP",
              "TLP:UPDATE-BLANK-COLOR",
              "#123456",
              5,
              Instant.parse("2026-01-02T10:00:00Z"));
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:UPDATE-BLANK-COLOR",
            "marking_definition_color": "",
            "marking_definition_order": 5
          }
          """;

      // Act
      mvc.perform(
              put(URI + "/{id}", Tenant.DEFAULT_TENANT_UUID, existing.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      // Assert
      MarkingDefinition reloaded = repository.findById(existing.getId()).orElseThrow();
      assertThat(reloaded.getColor()).isEqualTo("#123456");
    }

    @Test
    @DisplayName("given_whitespaceColor_should_rejectUpdate")
    void given_whitespaceColor_should_rejectUpdate() throws Exception {
      // Arrange
      MarkingDefinition existing =
          createPersistedMarkingDefinition(
              Tenant.DEFAULT_TENANT_UUID,
              "TLP",
              "TLP:UPDATE-WHITESPACE-COLOR",
              "#654321",
              6,
              Instant.parse("2026-01-03T10:00:00Z"));
      String body =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "TLP:UPDATE-WHITESPACE-COLOR",
            "marking_definition_color": "   ",
            "marking_definition_order": 6
          }
          """;

      // Act
      mvc.perform(
              put(URI + "/{id}", Tenant.DEFAULT_TENANT_UUID, existing.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      // Assert
      MarkingDefinition reloaded = repository.findById(existing.getId()).orElseThrow();
      assertThat(reloaded.getColor()).isEqualTo("#654321");
    }

    @Test
    @DisplayName("given_protectedDefinition_should_notDelete")
    void given_protectedDefinition_should_notDelete() throws Exception {
      // Arrange
      MarkingDefinition protectedDefinition =
          MarkingDefinitionFixture.createProtectedMarkingDefinition("TLP", "TLP:RED", 5, "#E53935");
      protectedDefinition.setTenant(
          entityManager.getReference(Tenant.class, Tenant.DEFAULT_TENANT_UUID));
      MarkingDefinition saved = repository.save(protectedDefinition);

      // Act & Assert
      mvc.perform(delete(URI + "/{id}", Tenant.DEFAULT_TENANT_UUID, saved.getId()).with(csrf()))
          .andExpect(status().is4xxClientError());
    }
  }

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_MARKING_DEFINITION,
        Capability.ACCESS_MARKING_DEFINITION
      })
  @DisplayName("Tenant isolation")
  class TenantIsolation {

    @Test
    @DisplayName("given_twoTenantRows_should_onlyListTenantARowUnderTenantAPath")
    void given_twoTenantRows_should_onlyListTenantARowUnderTenantAPath() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-tenant-a",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-tenant-b",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(),
              "TLP",
              "TENANT-A-ONLY",
              "#0066CC",
              10,
              Instant.parse("2026-01-01T10:00:00Z"));
      MarkingDefinition tenantBRow =
          createPersistedMarkingDefinition(
              tenantB.getId(),
              "TLP",
              "TENANT-B-ONLY",
              "#CC6600",
              20,
              Instant.parse("2026-01-01T11:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();

      // Act
      // A single test method stays on one tenant path: TenantScopeTransactionAspect refuses to
      // redefine the tenant scope already set on this transaction.
      String responseA =
          mvc.perform(
                  post(URI + "/search", tenantA.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> idsA = JsonPath.read(responseA, "$.content[*].marking_definition_id");
      assertThat(idsA).contains(tenantARow.getId()).doesNotContain(tenantBRow.getId());
    }

    @Test
    @DisplayName("given_twoTenantRows_should_onlyListTenantBRowUnderTenantBPath")
    void given_twoTenantRows_should_onlyListTenantBRowUnderTenantBPath() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-tenant-a",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-tenant-b",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(),
              "TLP",
              "TENANT-A-ONLY",
              "#0066CC",
              10,
              Instant.parse("2026-01-01T10:00:00Z"));
      MarkingDefinition tenantBRow =
          createPersistedMarkingDefinition(
              tenantB.getId(),
              "TLP",
              "TENANT-B-ONLY",
              "#CC6600",
              20,
              Instant.parse("2026-01-01T11:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();

      // Act
      // A single test method stays on one tenant path: TenantScopeTransactionAspect refuses to
      // redefine the tenant scope already set on this transaction.
      String responseB =
          mvc.perform(
                  post(URI + "/search", tenantB.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> idsB = JsonPath.read(responseB, "$.content[*].marking_definition_id");
      assertThat(idsB).contains(tenantBRow.getId()).doesNotContain(tenantARow.getId());
    }

    @Test
    @DisplayName("given_crossTenantUpdate_should_notUpdateRow")
    void given_crossTenantUpdate_should_notUpdateRow() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-update-a",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-update-b",
              Set.of(Capability.MANAGE_MARKING_DEFINITION, Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(),
              "TLP",
              "ORIGINAL-A",
              "#123456",
              3,
              Instant.parse("2026-02-01T10:00:00Z"));

      String updateBody =
          """
          {
            "marking_definition_type": "TLP",
            "marking_definition_definition": "MUTATED-B",
            "marking_definition_color": "#FFFFFF",
            "marking_definition_order": 99
          }
          """;

      // Act
      mvc.perform(
              put(URI + "/{id}", tenantB.getId(), tenantARow.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateBody)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());

      // Assert
      MarkingDefinition reloaded = repository.findById(tenantARow.getId()).orElseThrow();
      assertThat(reloaded.getDefinition()).isEqualTo("ORIGINAL-A");
      assertThat(reloaded.getColor()).isEqualTo("#123456");
      assertThat(reloaded.getOrder()).isEqualTo(3);
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.ACCESS_MARKING_DEFINITION)
  @DisplayName("Assigned check")
  class AssignedCheck {

    @BeforeEach
    void setUp() {
      markingDefinitionComposer.reset();
      endpointComposer.reset();
      tenantGroupComposer.reset();
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.ACCESS_MARKING_DEFINITION,
        autoJoinDefaultTenant = true)
    @DisplayName("given_markingCarriedByAnAsset_should_returnTrue")
    void given_markingCarriedByAnAsset_should_returnTrue() throws Exception {
      // Arrange
      MarkingDefinition marking = persistedMarking(Tenant.DEFAULT_TENANT_UUID);
      persistedAssetCarrying(marking);

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, marking.getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value(true));
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.ACCESS_MARKING_DEFINITION,
        autoJoinDefaultTenant = true)
    @DisplayName("given_markingGrantedOnlyToAGroup_should_returnTrue")
    void given_markingGrantedOnlyToAGroup_should_returnTrue() throws Exception {
      // Arrange - no entity carries the marking, so only groups_markings can make it assigned.
      MarkingDefinition marking = persistedMarking(Tenant.DEFAULT_TENANT_UUID);
      persistedGroupGranting(marking);

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, marking.getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value(true));
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.ACCESS_MARKING_DEFINITION,
        autoJoinDefaultTenant = true)
    @DisplayName("given_markingCarriedByNoEntity_should_returnFalse")
    void given_markingCarriedByNoEntity_should_returnFalse() throws Exception {
      // Arrange
      MarkingDefinition marking = persistedMarking(Tenant.DEFAULT_TENANT_UUID);
      entityManager.flush();

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, marking.getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value(false));
    }

    @Test
    @WithMockUser(
        withCapabilities = Capability.ACCESS_MARKING_DEFINITION,
        autoJoinDefaultTenant = true)
    @DisplayName("given_unknownId_should_returnNotFound")
    void given_unknownId_should_returnNotFound() throws Exception {
      // Arrange
      String unknownId = UUID.randomUUID().toString();

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, unknownId)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given_markingOfAnotherTenant_should_notRevealAssignment")
    void given_markingOfAnotherTenant_should_notRevealAssignment() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assigned-a", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assigned-b", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tenantAMarking = persistedMarking(tenantA.getId());
      persistedAssetCarrying(tenantAMarking);

      // Act & Assert - the tenant B caller must not learn whether tenant A's definition is in use.
      mvc.perform(
              get(URI + "/{id}/assigned", tenantB.getId(), tenantAMarking.getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(autoJoinDefaultTenant = true)
    @DisplayName("given_missingAccessCapability_should_returnForbidden")
    void given_missingAccessCapability_should_returnForbidden() throws Exception {
      // Arrange - tenant membership is granted, so the 403 can only come from @AccessControl.
      MarkingDefinition marking = persistedMarking(Tenant.DEFAULT_TENANT_UUID);
      persistedAssetCarrying(marking);

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, marking.getId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    private MarkingDefinition persistedMarking(String tenantId) {
      return markingDefinitionComposer
          .forMarkingDefinition(createDefaultMarkingDefinition())
          .withTenantId(tenantId)
          .persist()
          .get();
    }

    private void persistedAssetCarrying(MarkingDefinition marking) {
      Endpoint endpoint = EndpointFixture.createEndpoint("marking-assigned-" + uniqueDefinition());
      endpoint.setMarkingIds(new String[] {marking.getId()});
      endpointComposer.forEndpoint(endpoint).persist();
      entityManager.flush();
    }

    private void persistedGroupGranting(MarkingDefinition marking) {
      Group group = TenantGroupFixture.getGroup("marking-assigned-" + uniqueDefinition());
      group.getMarkings().add(marking);
      tenantGroupComposer.forGroup(group).persist();
      entityManager.flush();
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.ACCESS_MARKING_DEFINITION)
  @DisplayName("Search, filter and sort")
  class SearchFilterSort {

    @Test
    @DisplayName("given_typeAndColorFilters_should_returnMatchingRowsOnly")
    void given_typeAndColorFilters_should_returnMatchingRowsOnly() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-filter", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "MATCH", "#00AA00", 1, Instant.parse("2026-03-01T10:00:00Z"));
      MarkingDefinition wrongColor =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "WRONG-COLOR",
              "#AA0000",
              2,
              Instant.parse("2026-03-01T11:00:00Z"));
      MarkingDefinition wrongType =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "PAP",
              "WRONG-TYPE",
              "#00AA00",
              3,
              Instant.parse("2026-03-01T12:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  Filters.Filter.getNewDefaultEqualFilter(
                      "marking_definition_type", List.of("TLP")),
                  Filters.Filter.getNewDefaultEqualFilter(
                      "marking_definition_color", List.of("#00AA00")))));

      // Act
      String response =
          mvc.perform(
                  post(URI + "/search", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].marking_definition_id");
      assertThat(ids).containsExactly(matching.getId());
      assertThat(ids).doesNotContain(wrongColor.getId(), wrongType.getId());
    }

    @Test
    @DisplayName("given_textSearch_should_matchDefinition")
    void given_textSearch_should_matchDefinition() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-text-search", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "SEARCH-ME-DEFINITION",
              "#101010",
              4,
              Instant.parse("2026-04-01T09:00:00Z"));
      MarkingDefinition other =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "OTHER-DEFINITION",
              "#202020",
              5,
              Instant.parse("2026-04-01T10:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("SEARCH-ME");

      // Act
      String response =
          mvc.perform(
                  post(URI + "/search", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].marking_definition_id");
      assertThat(ids).containsExactly(matching.getId()).doesNotContain(other.getId());
    }

    @Test
    @DisplayName("given_sortByOrderDesc_should_returnHighestOrderFirst")
    void given_sortByOrderDesc_should_returnHighestOrderFirst() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-sort-order", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition low =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-LOW",
              "#111111",
              1,
              Instant.parse("2026-05-01T08:00:00Z"));
      MarkingDefinition high =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-HIGH",
              "#222222",
              9,
              Instant.parse("2026-05-01T09:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSorts(
          List.of(
              SortField.builder().property("marking_definition_order").direction("desc").build()));

      // Act
      String response =
          mvc.perform(
                  post(URI + "/search", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].marking_definition_id");
      assertThat(ids.indexOf(high.getId())).isLessThan(ids.indexOf(low.getId()));
    }

    @Test
    @DisplayName("given_sortByCreatedAtAsc_should_returnOldestFirst")
    void given_sortByCreatedAtAsc_should_returnOldestFirst() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-sort-created-at", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition oldest =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "CREATED-OLDEST",
              "#333333",
              2,
              Instant.parse("2026-06-01T08:00:00Z"));
      MarkingDefinition newest =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "CREATED-NEWEST",
              "#444444",
              3,
              Instant.parse("2026-06-01T10:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSorts(
          List.of(
              SortField.builder()
                  .property("marking_definition_created_at")
                  .direction("asc")
                  .build()));

      // Act
      String response =
          mvc.perform(
                  post(URI + "/search", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].marking_definition_id");
      assertThat(ids.indexOf(oldest.getId())).isLessThan(ids.indexOf(newest.getId()));
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.ACCESS_MARKING_DEFINITION)
  @DisplayName("GET /options - search by name")
  class OptionsByName {

    @Test
    @DisplayName("given_searchTextMatchingType_should_returnMatchingOption")
    void given_searchTextMatchingType_should_returnMatchingOption() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-type", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(), "PAP", "AMBER", "#FFB300", 6, Instant.parse("2026-07-01T08:00:00Z"));
      MarkingDefinition other =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "GREEN-CUSTOM",
              "#4CAF50",
              7,
              Instant.parse("2026-07-01T09:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  get(URI + "/options", tenant.getId())
                      .queryParam("searchText", "pap")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactly(matching.getId()).doesNotContain(other.getId());
    }

    @Test
    @DisplayName("given_searchTextMatchingDefinition_should_returnMatchingOption")
    void given_searchTextMatchingDefinition_should_returnMatchingOption() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-def", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "BESPOKE-CUSTOM-LABEL",
              "#123123",
              6,
              Instant.parse("2026-07-02T08:00:00Z"));
      MarkingDefinition other =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "UNRELATED-LABEL",
              "#321321",
              7,
              Instant.parse("2026-07-02T09:00:00Z"));

      // Act
      // lower-case search text also exercises the case-insensitive match on the stored definition.
      String response =
          mvc.perform(
                  get(URI + "/options", tenant.getId())
                      .queryParam("searchText", "bespoke-custom")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactly(matching.getId()).doesNotContain(other.getId());
    }

    @Test
    @DisplayName("given_noSearchText_should_returnOptionsOrderedByOrderAscending")
    void given_noSearchText_should_returnOptionsOrderedByOrderAscending() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-order", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition lowOrder =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-OPTION-LOW",
              "#111111",
              0,
              Instant.parse("2026-07-03T08:00:00Z"));
      MarkingDefinition highOrder =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-OPTION-HIGH",
              "#222222",
              100,
              Instant.parse("2026-07-03T09:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  get(URI + "/options", tenant.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids.indexOf(lowOrder.getId())).isLessThan(ids.indexOf(highOrder.getId()));
    }

    @Test
    @DisplayName("given_definitionAlreadyPrefixedWithType_should_useStoredDefinitionAsLabel")
    void given_definitionAlreadyPrefixedWithType_should_useStoredDefinitionAsLabel()
        throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-canonical-prefixed",
              Set.of(Capability.ACCESS_MARKING_DEFINITION));
      createPersistedMarkingDefinition(
          tenant.getId(),
          "TLP",
          "TLP:ALREADY-PREFIXED-TEST",
          "#E53935",
          6,
          Instant.parse("2026-07-04T08:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  get(URI + "/options", tenant.getId())
                      .queryParam("searchText", "already-prefixed")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: the definition already carries the "TLP:" prefix, so the label must not double it.
      List<String> labels = JsonPath.read(response, "$[*].label");
      assertThat(labels).containsExactly("TLP:ALREADY-PREFIXED-TEST");
    }

    @Test
    @DisplayName("given_definitionStoredBareWithoutTypePrefix_should_prefixLabelWithType")
    void given_definitionStoredBareWithoutTypePrefix_should_prefixLabelWithType() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-canonical-bare", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      createPersistedMarkingDefinition(
          tenant.getId(), "PAP", "AMBER", "#FFB300", 6, Instant.parse("2026-07-05T08:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  get(URI + "/options", tenant.getId())
                      .queryParam("searchText", "pap")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: the stored definition is bare ("AMBER"), so the label must prefix it with the type.
      List<String> labels = JsonPath.read(response, "$[*].label");
      assertThat(labels).containsExactly("PAP:AMBER");
    }

    @Test
    @DisplayName("given_tenantBPath_should_excludeTenantARowFromNameSearch")
    void given_tenantBPath_should_excludeTenantARowFromNameSearch() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-cross-a", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-name-cross-b", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(),
              "TLP",
              "ONLY-IN-TENANT-A-OPTION",
              "#0066CC",
              6,
              Instant.parse("2026-07-06T08:00:00Z"));
      MarkingDefinition tenantBRow =
          createPersistedMarkingDefinition(
              tenantB.getId(),
              "TLP",
              "ONLY-IN-TENANT-B-OPTION",
              "#CC6600",
              7,
              Instant.parse("2026-07-06T09:00:00Z"));

      // Act
      // A single test method stays on one tenant path: TenantScopeTransactionAspect refuses to
      // redefine the tenant scope already set on this transaction.
      String response =
          mvc.perform(
                  get(URI + "/options", tenantB.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).contains(tenantBRow.getId()).doesNotContain(tenantARow.getId());
    }
  }

  @Nested
  @WithMockUser(withCapabilities = Capability.ACCESS_MARKING_DEFINITION)
  @DisplayName("POST /options - resolve by id")
  class OptionsById {

    @Test
    @DisplayName("given_knownIds_should_resolveLabelsForEachId")
    void given_knownIds_should_resolveLabelsForEachId() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-id-known", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition rowA =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ID-RESOLVE-A",
              "#AABBCC",
              6,
              Instant.parse("2026-07-07T08:00:00Z"));
      MarkingDefinition rowB =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "PAP",
              "ID-RESOLVE-B",
              "#CCBBAA",
              7,
              Instant.parse("2026-07-07T09:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  post(URI + "/options", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(List.of(rowA.getId(), rowB.getId())))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactlyInAnyOrder(rowA.getId(), rowB.getId());
      List<String> labels = JsonPath.read(response, "$[*].label");
      assertThat(labels).containsExactlyInAnyOrder("TLP:ID-RESOLVE-A", "PAP:ID-RESOLVE-B");
    }

    @Test
    @DisplayName("given_unknownId_should_excludeItFromResult")
    void given_unknownId_should_excludeItFromResult() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-id-unknown", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition known =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ID-RESOLVE-KNOWN",
              "#AABBCC",
              6,
              Instant.parse("2026-07-08T08:00:00Z"));

      // Act
      String response =
          mvc.perform(
                  post(URI + "/options", tenant.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(List.of(known.getId(), UUID.randomUUID().toString())))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: an id that resolves to no row is silently dropped, not an error.
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactly(known.getId());
    }

    @Test
    @DisplayName("given_emptyIdList_should_returnEmptyList")
    void given_emptyIdList_should_returnEmptyList() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-id-empty", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      // Act & Assert
      mvc.perform(
              post(URI + "/options", tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of()))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("given_idFromAnotherTenant_should_excludeCrossTenantId")
    void given_idFromAnotherTenant_should_excludeCrossTenantId() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-id-cross-a", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-options-id-cross-b", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(),
              "TLP",
              "CROSS-TENANT-ID-A",
              "#0066CC",
              6,
              Instant.parse("2026-07-09T08:00:00Z"));
      MarkingDefinition tenantBRow =
          createPersistedMarkingDefinition(
              tenantB.getId(),
              "TLP",
              "CROSS-TENANT-ID-B",
              "#CC6600",
              7,
              Instant.parse("2026-07-09T09:00:00Z"));

      // Act
      // Explicitly request both ids under tenant B's path: tenant A's row must not resolve even
      // though its id was asked for by name.
      String response =
          mvc.perform(
                  post(URI + "/options", tenantB.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(List.of(tenantARow.getId(), tenantBRow.getId())))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactly(tenantBRow.getId());
    }
  }

  @Nested
  @DisplayName("Options authorization")
  class OptionsAuthorization {

    @Test
    @DisplayName("given_noAuthentication_should_return401OnOptionsByName")
    void given_noAuthentication_should_return401OnOptionsByName() throws Exception {
      // Act & Assert
      mvc.perform(get(URI + "/options", Tenant.DEFAULT_TENANT_UUID).with(csrf()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("given_noAuthentication_should_return401OnOptionsById")
    void given_noAuthentication_should_return401OnOptionsById() throws Exception {
      // Act & Assert
      mvc.perform(
              post(URI + "/options", Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of()))
                  .with(csrf()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("given_noCapability_should_forbidOptionsByName")
    void given_noCapability_should_forbidOptionsByName() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("marking-options-authz-nocap-name");

      // Act & Assert
      mvc.perform(get(URI + "/options", tenant.getId()).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("given_noCapability_should_forbidOptionsById")
    void given_noCapability_should_forbidOptionsById() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("marking-options-authz-nocap-id");

      // Act & Assert
      mvc.perform(
              post(URI + "/options", tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of()))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = Capability.ACCESS_MARKING_DEFINITION)
    @DisplayName("given_tenantCallerIsNotMemberOf_should_forbidOptionsByName")
    void given_tenantCallerIsNotMemberOf_should_forbidOptionsByName() throws Exception {
      // Arrange: capability granted in a tenant the caller belongs to, but the request targets a
      // different tenant the caller was never attached to.
      tenantIsolationTestHelper.createTenantWithCapabilities(
          "marking-options-authz-home", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      Tenant foreignTenant =
          tenantIsolationTestHelper.createTenant(
              "marking-options-authz-foreign-" + UUID.randomUUID());

      // Act & Assert
      mvc.perform(get(URI + "/options", foreignTenant.getId()).with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  private MarkingDefinition createPersistedMarkingDefinition(
      String tenantId, String type, String definition, String color, int order, Instant createdAt) {
    MarkingDefinition markingDefinition =
        MarkingDefinitionFixture.createMarkingDefinition(type, definition, order, color, createdAt);
    markingDefinition.setTenant(entityManager.getReference(Tenant.class, tenantId));
    return repository.save(markingDefinition);
  }
}
