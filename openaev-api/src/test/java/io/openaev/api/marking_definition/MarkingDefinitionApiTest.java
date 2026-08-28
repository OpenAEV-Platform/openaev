package io.openaev.api.marking_definition;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Filters;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Marking definition API")
class MarkingDefinitionApiTest extends IntegrationTest {

  private static final String URI = "/api/tenants/{tenantId}/marking_definitions";

  @Autowired private MockMvc mvc;
  @Autowired private MarkingDefinitionRepository repository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  @Nested
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_MARKING_DEFINITION,
        Capability.DELETE_MARKING_DEFINITION,
        Capability.ACCESS_MARKING_DEFINITION
      })
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
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.marking_definition_type").value("TLP"))
          .andExpect(jsonPath("$.marking_definition_definition").value("TLP:BLUE"))
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
                  .content(body))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_protectedDefinition_should_notDelete")
    void given_protectedDefinition_should_notDelete() throws Exception {
      // Arrange
      MarkingDefinition protectedDefinition = new MarkingDefinition();
      protectedDefinition.setType("TLP");
      protectedDefinition.setDefinition("TLP:RED");
      protectedDefinition.setColor("#E53935");
      protectedDefinition.setOrder(5);
      protectedDefinition.setProtectedDefinition(true);
      protectedDefinition.setTenant(
          entityManager.getReference(Tenant.class, Tenant.DEFAULT_TENANT_UUID));
      MarkingDefinition saved = repository.save(protectedDefinition);

      // Act & Assert
      mvc.perform(delete(URI + "/{id}", Tenant.DEFAULT_TENANT_UUID, saved.getId()))
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
    @DisplayName("given_twoTenantRows_should_onlyListRowsFromRequestedTenant")
    void given_twoTenantRows_should_onlyListRowsFromRequestedTenant() throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-tenant-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-tenant-b");

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
      String responseA =
          mvc.perform(
                  post(URI + "/search", tenantA.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String responseB =
          mvc.perform(
                  post(URI + "/search", tenantB.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> idsA = JsonPath.read(responseA, "$.content[*].marking_definition_id");
      List<String> idsB = JsonPath.read(responseB, "$.content[*].marking_definition_id");

      assertThat(idsA).contains(tenantARow.getId()).doesNotContain(tenantBRow.getId());
      assertThat(idsB).contains(tenantBRow.getId()).doesNotContain(tenantARow.getId());
    }

    @Test
    @DisplayName("given_crossTenantUpdate_should_notUpdateRow")
    void given_crossTenantUpdate_should_notUpdateRow() throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-update-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-update-b");

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
                  .content(updateBody))
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
  @DisplayName("Search, filter and sort")
  class SearchFilterSort {

    @Test
    @DisplayName("given_typeAndColorFilters_should_returnMatchingRowsOnly")
    void given_typeAndColorFilters_should_returnMatchingRowsOnly() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-filter");

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
                      .accept(MediaType.APPLICATION_JSON))
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
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-text-search");

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
                      .accept(MediaType.APPLICATION_JSON))
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
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("marking-sort-order");

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
                      .accept(MediaType.APPLICATION_JSON))
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
          tenantIsolationTestHelper.createTenantWithCurrentUser("marking-sort-created-at");

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
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].marking_definition_id");
      assertThat(ids.indexOf(oldest.getId())).isLessThan(ids.indexOf(newest.getId()));
    }
  }

  private MarkingDefinition createPersistedMarkingDefinition(
      String tenantId, String type, String definition, String color, int order, Instant createdAt) {
    MarkingDefinition markingDefinition = new MarkingDefinition();
    markingDefinition.setType(type);
    markingDefinition.setDefinition(definition);
    markingDefinition.setColor(color);
    markingDefinition.setOrder(order);
    markingDefinition.setProtectedDefinition(false);
    markingDefinition.setTenant(entityManager.getReference(Tenant.class, tenantId));
    markingDefinition.setCreatedAt(createdAt);
    markingDefinition.setUpdatedAt(createdAt);
    return repository.save(markingDefinition);
  }
}
