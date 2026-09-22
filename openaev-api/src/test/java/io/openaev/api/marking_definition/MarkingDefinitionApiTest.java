package io.openaev.api.marking_definition;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import java.util.Set;
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
    @DisplayName("given_orderEqualsFilter_should_returnExactMatch")
    void given_orderEqualsFilter_should_returnExactMatch() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-order-eq", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-EQ",
              "#00AA00",
              7,
              Instant.parse("2026-03-02T10:00:00Z"));
      MarkingDefinition other =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-NOT-EQ",
              "#AA0000",
              8,
              Instant.parse("2026-03-02T11:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  new Filters.Filter(
                      "order-eq",
                      "marking_definition_order",
                      Filters.FilterMode.or,
                      List.of("7"),
                      Filters.FilterOperator.eq))));

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
    @DisplayName("given_orderNotEqualsFilter_should_excludeExactMatch")
    void given_orderNotEqualsFilter_should_excludeExactMatch() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-order-not-eq", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition excluded =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-NOT-EQ",
              "#00AA00",
              4,
              Instant.parse("2026-03-03T10:00:00Z"));
      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-OTHER",
              "#AA0000",
              9,
              Instant.parse("2026-03-03T11:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  new Filters.Filter(
                      "order-not-eq",
                      "marking_definition_order",
                      Filters.FilterMode.or,
                      List.of("4"),
                      Filters.FilterOperator.not_eq))));

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
      assertThat(ids).containsExactly(matching.getId()).doesNotContain(excluded.getId());
    }

    @Test
    @DisplayName("given_orderGreaterThanFilter_should_returnHigherOrdersOnly")
    void given_orderGreaterThanFilter_should_returnHigherOrdersOnly() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-order-gt", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition lower =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-LOWER",
              "#00AA00",
              2,
              Instant.parse("2026-03-04T10:00:00Z"));
      MarkingDefinition higher =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-HIGHER",
              "#AA0000",
              6,
              Instant.parse("2026-03-04T11:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  new Filters.Filter(
                      "order-gt",
                      "marking_definition_order",
                      Filters.FilterMode.or,
                      List.of("4"),
                      Filters.FilterOperator.gt))));

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
      assertThat(ids).containsExactly(higher.getId()).doesNotContain(lower.getId());
    }

    @Test
    @DisplayName("given_orderEmptyFilter_should_returnNoRows")
    void given_orderEmptyFilter_should_returnNoRows() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-order-empty", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      createPersistedMarkingDefinition(
          tenant.getId(),
          "TLP",
          "ORDER-EMPTY",
          "#00AA00",
          5,
          Instant.parse("2026-03-05T10:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  new Filters.Filter(
                      "order-empty",
                      "marking_definition_order",
                      Filters.FilterMode.or,
                      List.of(),
                      Filters.FilterOperator.empty))));

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
      assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("given_orderNotEmptyFilter_should_returnRows")
    void given_orderNotEmptyFilter_should_returnRows() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "marking-order-not-empty", Set.of(Capability.ACCESS_MARKING_DEFINITION));

      MarkingDefinition matching =
          createPersistedMarkingDefinition(
              tenant.getId(),
              "TLP",
              "ORDER-NOT-EMPTY",
              "#00AA00",
              5,
              Instant.parse("2026-03-06T10:00:00Z"));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  new Filters.Filter(
                      "order-not-empty",
                      "marking_definition_order",
                      Filters.FilterMode.or,
                      List.of(),
                      Filters.FilterOperator.not_empty))));

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
