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
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
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
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
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
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private MarkingClearanceCacheManager markingClearanceCacheManager;

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
      // Creating is bounded by the caller's own clearance on the type.
      grantToCaller(
          createPersistedMarkingDefinition(
              Tenant.DEFAULT_TENANT_UUID,
              "TLP",
              "TLP:CLEARANCE-CEILING",
              "#000000",
              100,
              Instant.now()));

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
      // Cleared for it, so the refusal can only come from the protection.
      grantToCaller(saved);

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

      // Cleared for both, so only the tenant scope can exclude the other tenant's row.
      grantToCaller(tenantARow, tenantBRow);
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

      // Cleared for both, so only the tenant scope can exclude the other tenant's row.
      grantToCaller(tenantARow, tenantBRow);
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
      grantCeilingAbove(Tenant.DEFAULT_TENANT_UUID, marking);

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
      grantCeilingAbove(Tenant.DEFAULT_TENANT_UUID, marking);

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
      grantCeilingAbove(Tenant.DEFAULT_TENANT_UUID, marking);
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
    @WithMockUser(
        withCapabilities = Capability.ACCESS_MARKING_DEFINITION,
        autoJoinDefaultTenant = true)
    @DisplayName("given_markingAboveOwnClearance_should_notRevealAssignment")
    void given_markingAboveOwnClearance_should_notRevealAssignment() throws Exception {
      // Arrange - search does not reveal it, so whether it is in use must not leak either.
      MarkingDefinition marking = persistedMarking(Tenant.DEFAULT_TENANT_UUID);
      persistedAssetCarrying(marking);

      // Act & Assert
      mvc.perform(
              get(URI + "/{id}/assigned", Tenant.DEFAULT_TENANT_UUID, marking.getId())
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

      grantToCaller(matching, wrongColor, wrongType);
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

      grantToCaller(matching, other);
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

      grantToCaller(low, high);
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

      grantToCaller(oldest, newest);
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
  @DisplayName("GET /assignable")
  class Assignable {

    @Test
    @DisplayName("given_partialClearance_should_onlyReturnAssignableDefinitionsByOrder")
    void given_partialClearance_should_onlyReturnAssignableDefinitionsByOrder() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assignable-partial", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tlpMid =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "ASSIGNABLE-MID", "#AAAA00", 62, Instant.now());
      MarkingDefinition tlpLow =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "ASSIGNABLE-LOW", "#00AA00", 61, Instant.now());
      MarkingDefinition tlpHigh =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "NOT-ASSIGNABLE-HIGH", "#AA0000", 63, Instant.now());
      MarkingDefinition papOther =
          createPersistedMarkingDefinition(
              tenant.getId(), "PAP", "NOT-ASSIGNABLE-PAP", "#0000AA", 61, Instant.now());
      // Clearance is cumulative per type: holding the mid TLP also clears the lower one.
      grantToCaller(tlpMid);

      // Act
      List<String> ids = assignableIds(tenant.getId());

      // Assert
      assertThat(ids)
          .containsExactly(tlpLow.getId(), tlpMid.getId())
          .doesNotContain(tlpHigh.getId(), papOther.getId());
    }

    @Test
    @DisplayName("given_noClearance_should_returnNoDefinition")
    void given_noClearance_should_returnNoDefinition() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assignable-none", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      createPersistedMarkingDefinition(
          tenant.getId(), "TLP", "NOT-ASSIGNABLE", "#AA0000", 61, Instant.now());

      // Act
      List<String> ids = assignableIds(tenant.getId());

      // Assert
      assertThat(ids).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_otherAdmin_should_onlyReturnGrantedDefinitions")
    void given_otherAdmin_should_onlyReturnGrantedDefinitions() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assignable-admin", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tlp =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "ADMIN-TLP", "#AA0000", 61, Instant.now());
      MarkingDefinition pap =
          createPersistedMarkingDefinition(
              tenant.getId(), "PAP", "ADMIN-PAP", "#0000AA", 61, Instant.now());

      grantToCaller(tlp);

      // Act
      List<String> ids = assignableIds(tenant.getId());

      // Assert
      assertThat(ids).containsExactly(tlp.getId());
    }

    @Test
    @DisplayName("given_tenantBPath_should_excludeTenantADefinition")
    void given_tenantBPath_should_excludeTenantADefinition() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assignable-cross-a", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-assignable-cross-b", Set.of(Capability.ACCESS_MARKING_DEFINITION));
      MarkingDefinition tenantARow =
          createPersistedMarkingDefinition(
              tenantA.getId(), "TLP", "ONLY-IN-TENANT-A", "#0066CC", 61, Instant.now());
      MarkingDefinition tenantBRow =
          createPersistedMarkingDefinition(
              tenantB.getId(), "TLP", "ONLY-IN-TENANT-B", "#CC6600", 61, Instant.now());
      // Cleared for both, so only the tenant scope can exclude the other tenant's row.
      grantToCaller(tenantARow, tenantBRow);

      // Act
      List<String> ids = assignableIds(tenantB.getId());

      // Assert
      assertThat(ids).contains(tenantBRow.getId()).doesNotContain(tenantARow.getId());
    }

    @Test
    @WithMockUser
    @DisplayName("given_noCapability_should_returnClearedDefinitions")
    void given_noCapability_should_returnClearedDefinitions() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("marking-assignable-no-capability");
      MarkingDefinition held =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-WITHOUT-CAPABILITY", "#00AA00", 61, Instant.now());
      grantToCaller(held);

      // Act
      List<String> ids = assignableIds(tenant.getId());

      // Assert
      assertThat(ids).containsExactly(held.getId());
    }

    private List<String> assignableIds(String tenantId) throws Exception {
      String response =
          mvc.perform(
                  get(URI + "/assignable", tenantId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$[*].marking_definition_id");
    }
  }

  @Nested
  @DisplayName("GET /assignable - authentication")
  class AssignableAuthentication {

    @Test
    @DisplayName("given_noAuthentication_should_return401")
    void given_noAuthentication_should_return401() throws Exception {
      // Act & Assert
      mvc.perform(get(URI + "/assignable", Tenant.DEFAULT_TENANT_UUID).with(csrf()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_MARKING_DEFINITION,
        Capability.DELETE_MARKING_DEFINITION,
        Capability.ACCESS_MARKING_DEFINITION
      })
  @DisplayName("Write clearance")
  class WriteClearance {

    @Test
    @DisplayName("given_orderAboveOwnClearance_should_forbidCreation")
    void given_orderAboveOwnClearance_should_forbidCreation() throws Exception {
      // Arrange
      Tenant tenant = tenantWithWriteCapabilities("marking-write-create-above");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));

      // Act
      mvc.perform(
              post(URI, tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "TLP:ABOVE-CLEARANCE", 63)))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertThat(
              repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
                  "TLP", "TLP:ABOVE-CLEARANCE", tenant.getId(), null))
          .isFalse();
    }

    @Test
    @DisplayName("given_orderEqualToOwnClearance_should_createDefinition")
    void given_orderEqualToOwnClearance_should_createDefinition() throws Exception {
      // Arrange
      Tenant tenant = tenantWithWriteCapabilities("marking-write-create-equal");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));

      // Act & Assert
      mvc.perform(
              post(URI, tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "TLP:AT-CLEARANCE", 62)))
                  .with(csrf()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.marking_definition_order").value(62));
    }

    @Test
    @DisplayName("given_typeNotInTenant_should_createDefinitionWithoutClearance")
    void given_typeNotInTenant_should_createDefinitionWithoutClearance() throws Exception {
      // Arrange - the caller holds no marking at all.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-create-new-type");

      // Act & Assert
      mvc.perform(
              post(URI, tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("NEWTYPE", "NEWTYPE:HIGH", 999)))
                  .with(csrf()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.marking_definition_type").value("NEWTYPE"));
    }

    @Test
    @DisplayName("given_existingTypeNotHeld_should_forbidCreation")
    void given_existingTypeNotHeld_should_forbidCreation() throws Exception {
      // Arrange - holding TLP says nothing about PAP, which the tenant already has.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-create-unheld-type");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-TOP", "#AA0000", 100, Instant.now()));
      createPersistedMarkingDefinition(
          tenant.getId(), "PAP", "PAP:UNHELD", "#00AA00", 50, Instant.now());

      // Act
      mvc.perform(
              post(URI, tenant.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("PAP", "PAP:LOW", 0)))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertThat(
              repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
                  "PAP", "PAP:LOW", tenant.getId(), null))
          .isFalse();
    }

    @Test
    @DisplayName("given_definitionAboveOwnClearance_should_notLowerIt")
    void given_definitionAboveOwnClearance_should_notLowerIt() throws Exception {
      // Arrange - lowering it into the caller's range would grant them every row it marks.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-update-uncleared");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));
      MarkingDefinition uncleared =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "NOT-CLEARED-HIGH", "#AA0000", 63, Instant.now());

      // Act
      mvc.perform(
              put(URI + "/{id}", tenant.getId(), uncleared.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "NOT-CLEARED-HIGH", 1)))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(repository.findById(uncleared.getId()).orElseThrow().getOrder()).isEqualTo(63);
    }

    @Test
    @DisplayName("given_orderRaisedAboveOwnClearance_should_forbidUpdate")
    void given_orderRaisedAboveOwnClearance_should_forbidUpdate() throws Exception {
      // Arrange - raising the caller's own top level would lock them out of it.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-update-raise");
      MarkingDefinition held =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now());
      grantToCaller(held);

      // Act
      mvc.perform(
              put(URI + "/{id}", tenant.getId(), held.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "HELD-MID", 63)))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      assertThat(repository.findById(held.getId()).orElseThrow().getOrder()).isEqualTo(62);
    }

    @Test
    @DisplayName("given_orderAboveOwnClearanceAndHiddenDuplicateName_should_forbidUpdate")
    void given_orderAboveOwnClearanceAndHiddenDuplicateName_should_forbidUpdate() throws Exception {
      // Arrange - a duplicate-name 400 instead of the 403 an unknown name gets would reveal the
      // hidden definition through a request the caller may not make.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-update-hidden-duplicate");
      MarkingDefinition held =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now());
      grantToCaller(held);
      createPersistedMarkingDefinition(
          tenant.getId(), "TLP", "NOT-CLEARED-HIGH", "#AA0000", 63, Instant.now());

      // Act
      mvc.perform(
              put(URI + "/{id}", tenant.getId(), held.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "NOT-CLEARED-HIGH", 64)))
                  .with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      MarkingDefinition reloaded = repository.findById(held.getId()).orElseThrow();
      assertThat(reloaded.getDefinition()).isEqualTo("HELD-MID");
      assertThat(reloaded.getOrder()).isEqualTo(62);
    }

    @Test
    @DisplayName("given_orderLoweredWithinOwnClearance_should_update")
    void given_orderLoweredWithinOwnClearance_should_update() throws Exception {
      // Arrange
      Tenant tenant = tenantWithWriteCapabilities("marking-write-update-lower");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));
      MarkingDefinition covered =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "COVERED-LOW", "#00AA00", 61, Instant.now());

      // Act & Assert
      mvc.perform(
              put(URI + "/{id}", tenant.getId(), covered.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input("TLP", "COVERED-LOW", 60)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.marking_definition_order").value(60));
    }

    @Test
    @DisplayName("given_definitionAboveOwnClearance_should_notDeleteIt")
    void given_definitionAboveOwnClearance_should_notDeleteIt() throws Exception {
      // Arrange - the delete-time scrub would declassify every row the caller cannot see.
      Tenant tenant = tenantWithWriteCapabilities("marking-write-delete-uncleared");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));
      MarkingDefinition uncleared =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "NOT-CLEARED-HIGH", "#AA0000", 63, Instant.now());

      // Act
      mvc.perform(delete(URI + "/{id}", tenant.getId(), uncleared.getId()).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(repository.findById(uncleared.getId())).isPresent();
    }

    @Test
    @DisplayName("given_definitionWithinOwnClearance_should_deleteIt")
    void given_definitionWithinOwnClearance_should_deleteIt() throws Exception {
      // Arrange
      Tenant tenant = tenantWithWriteCapabilities("marking-write-delete-covered");
      grantToCaller(
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "HELD-MID", "#AAAA00", 62, Instant.now()));
      MarkingDefinition covered =
          createPersistedMarkingDefinition(
              tenant.getId(), "TLP", "COVERED-LOW", "#00AA00", 61, Instant.now());

      // Act
      mvc.perform(delete(URI + "/{id}", tenant.getId(), covered.getId()).with(csrf()))
          .andExpect(status().isNoContent());

      // Assert
      assertThat(repository.findById(covered.getId())).isEmpty();
    }

    private Tenant tenantWithWriteCapabilities(String name) throws Exception {
      return tenantIsolationTestHelper.createTenantWithCapabilities(
          name,
          Set.of(
              Capability.MANAGE_MARKING_DEFINITION,
              Capability.DELETE_MARKING_DEFINITION,
              Capability.ACCESS_MARKING_DEFINITION));
    }

    private MarkingDefinitionInput input(String type, String definition, int order) {
      return new MarkingDefinitionInput(type, definition, "#123456", order);
    }
  }

  /**
   * Grants the markings to the caller through a group: the assigned check, the assignable list and
   * the writes only reach the definitions the caller's clearance covers.
   */
  private void grantToCaller(MarkingDefinition... markings) {
    Group group = TenantGroupFixture.getGroup("marking-clearance-" + uniqueDefinition());
    group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
    group.getMarkings().addAll(List.of(markings));
    tenantGroupComposer.forGroup(group).persist();
    entityManager.flush();
    markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());
  }

  /**
   * Covers {@code marking} through a higher definition of its type rather than a direct grant: a
   * grant is itself an assignment, which would skew an assigned check.
   */
  private void grantCeilingAbove(String tenantId, MarkingDefinition marking) {
    grantToCaller(
        createPersistedMarkingDefinition(
            tenantId,
            marking.getType(),
            uniqueDefinition(),
            "#000000",
            marking.getOrder() + 1,
            Instant.now()));
  }

  private MarkingDefinition createPersistedMarkingDefinition(
      String tenantId, String type, String definition, String color, int order, Instant createdAt) {
    MarkingDefinition markingDefinition =
        MarkingDefinitionFixture.createMarkingDefinition(type, definition, order, color, createdAt);
    markingDefinition.setTenant(entityManager.getReference(Tenant.class, tenantId));
    return repository.save(markingDefinition);
  }
}
