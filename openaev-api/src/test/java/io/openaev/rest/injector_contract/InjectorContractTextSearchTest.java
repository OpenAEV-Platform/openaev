package io.openaev.rest.injector_contract;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.InjectorContract;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * #9876: the injector contract text search matches the name of a linked attack pattern and the
 * payload description, not only the contract title and the attack pattern external id.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Injector contract text search")
class InjectorContractTextSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;

  @BeforeEach
  void before() {
    injectorContractComposer.reset();
  }

  private AttackPattern attackPattern(String externalId, String name) {
    // A unique suffix keeps the stix id and external id clear of any attack pattern already stored.
    AttackPattern attackPattern =
        AttackPatternFixture.createAttackPatternsWithExternalId(
            externalId + "-" + UUID.randomUUID(), TenantContext.getCurrentTenant());
    attackPattern.setName(name);
    return attackPattern;
  }

  private InjectorContract persistContract(String title, AttackPattern... attackPatterns)
      throws Exception {
    InjectorContract contract = InjectorContractFixture.createInjectorContract(Map.of("en", title));
    InjectorContractComposer.Composer composer =
        injectorContractComposer.forInjectorContract(contract);
    for (AttackPattern attackPattern : attackPatterns) {
      composer.withAttackPattern(attackPatternComposer.forAttackPattern(attackPattern));
    }
    composer.persist();
    entityManager.flush();
    return contract;
  }

  private ResultActions search(String textSearch) throws Exception {
    SearchPaginationInput input = PaginationFixture.getDefault().textSearch(textSearch).build();
    return mvc.perform(
            post("/api/injector_contracts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("Through the search route")
  class ThroughTheSearchRoute {

    @Test
    @DisplayName("A contract is found by the name of its linked attack pattern, whole or in part")
    void given_attack_pattern_name_should_return_the_linked_contract() throws Exception {
      InjectorContract contract =
          persistContract(
              "Dump browser stores " + UUID.randomUUID(),
              attackPattern("T1555.003", "Credentials from Web Browsers"));

      for (String text : List.of("Credentials from Web Browsers", "web browsers", "T1555.003")) {
        search(text)
            .andExpect(jsonPath("$.content[*].injector_contract_id", hasItem(contract.getId())));
      }
    }

    @Test
    @DisplayName("A contract linked to Registry Run Keys is found by that name")
    void given_registry_run_keys_should_return_the_linked_contract() throws Exception {
      InjectorContract contract =
          persistContract(
              "Create a new Registry Key for Autorun " + UUID.randomUUID(),
              attackPattern("T1547.001", "Registry Run Keys / Startup Folder"));

      search("Registry Run Keys")
          .andExpect(jsonPath("$.content[*].injector_contract_id", hasItem(contract.getId())));
    }

    @Test
    @DisplayName("Several matching attack patterns never duplicate the contract")
    void given_several_matching_attack_patterns_should_return_the_contract_once()
        throws Exception {
      String marker = "dup-" + UUID.randomUUID();
      InjectorContract contract =
          persistContract(
              "Contract " + UUID.randomUUID(),
              attackPattern("T1000.001", marker + " first technique"),
              attackPattern("T1000.002", marker + " second technique"));

      search(marker)
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.numberOfElements").value(1))
          .andExpect(jsonPath("$.content[0].injector_contract_id").value(contract.getId()));
    }

    @Test
    @DisplayName("A title search is unchanged and does not match an unrelated contract")
    void given_title_should_return_only_the_titled_contract() throws Exception {
      String title = "Title search " + UUID.randomUUID();
      InjectorContract titled = persistContract(title);
      InjectorContract other =
          persistContract(
              "Unrelated " + UUID.randomUUID(), attackPattern("T1059.001", "PowerShell"));

      search(title)
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].injector_contract_id").value(titled.getId()))
          .andExpect(jsonPath("$.content[*].injector_contract_id", not(hasItem(other.getId()))));
    }
  }

  @Nested
  @DisplayName("Searchable properties")
  class SearchableProperties {

    private Map<String, PropertySchema> searchableByName() {
      return SchemaUtils.getSearchableProperties(SchemaUtils.schema(InjectorContract.class))
          .stream()
          .collect(Collectors.toMap(PropertySchema::getName, Function.identity(), (a, b) -> a));
    }

    @Test
    @DisplayName("The text search covers the title, attack patterns, vulnerabilities and payload")
    void searchCoversTitleAttackPatternsVulnerabilitiesAndPayloadDescription() {
      Map<String, PropertySchema> searchable = searchableByName();

      assertThat(searchable).containsKeys("labels", "attackPatterns", "vulnerabilities");
      assertThat(searchable.get("attackPatterns").getPath()).isEqualTo("attackPatterns.externalId");
      assertThat(searchable.get("vulnerabilities").getPath())
          .isEqualTo("vulnerabilities.externalId");
      assertThat(searchable.get("getAttackPatternNames").getPath())
          .isEqualTo("attackPatterns.name");
      assertThat(searchable.get("getPayloadDescription").getPath())
          .isEqualTo("payload.description");
    }

    @Test
    @DisplayName("The attack pattern name and payload description are search-only")
    void attackPatternNameAndPayloadDescriptionAreSearchOnly() {
      Map<String, PropertySchema> searchable = searchableByName();

      for (String name : List.of("getAttackPatternNames", "getPayloadDescription")) {
        assertThat(searchable.get(name).isFilterable()).isFalse();
        assertThat(searchable.get(name).isSortable()).isFalse();
      }
    }
  }
}
