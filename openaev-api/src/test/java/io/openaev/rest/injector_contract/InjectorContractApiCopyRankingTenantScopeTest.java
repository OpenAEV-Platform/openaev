package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.utils.CsvType;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A contract id held by several tenants of the caller's scope is read once, from one ranked copy:
 * the default tenant's copy when the scope holds it, else the copy of the lowest tenant id, among
 * the copies that match the request's filter and text search. The search, its counts and the
 * contract GET with no selector (which serves the default tenant's copy) name the same copy.
 *
 * <p>The default tenant id is neither the lowest nor the highest of the tenant ids, so each case
 * creates its second tenant on the side of the default id that makes the ranking observable.
 *
 * <p>Deliberately NOT {@code @Transactional}: each HTTP call runs in its own transaction with its
 * own tenant scope, the way a real request does.
 */
@DisplayName("Contract copy ranking for a caller of several tenants")
@WithMockUser(isAdmin = true)
class InjectorContractApiCopyRankingTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String LABEL_PREFIX = "copy-rank-";
  private static final int MAX_TENANT_ATTEMPTS = 80;

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private final List<String> createdTenants = new ArrayList<>();
  private final List<String[]> createdRows = new ArrayList<>();
  private String contractId;
  private String tagId;

  @AfterEach
  void cleanup() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    if (contractId != null) {
      jdbc.update("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
    }
    if (tagId != null) {
      jdbc.update("DELETE FROM tags WHERE tag_id = ?", tagId);
    }
    createdRows.forEach(
        row -> jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]));
    createdRows.clear();
    createdTenants.forEach(tenantHelper::deleteCommittedTenants);
    createdTenants.clear();
    contractId = null;
    tagId = null;
  }

  /** A tenant of the caller whose id sorts below (or above) the default tenant's id. */
  private String tenantOnTheSideOfDefault(boolean below) throws Exception {
    for (int attempt = 0; attempt < MAX_TENANT_ATTEMPTS; attempt++) {
      String tenantId = tenantHelper.createTenantWithCurrentUser("ic-rank").getId();
      createdTenants.add(tenantId);
      if ((tenantId.compareTo(Tenant.DEFAULT_TENANT_UUID) < 0) == below) {
        builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantId);
        return tenantId;
      }
      createdTenants.remove(tenantId);
      tenantHelper.deleteCommittedTenants(tenantId);
    }
    throw new IllegalStateException("no tenant id on the requested side of the default id");
  }

  /** The caller of the default tenant and of {@code other}, with the ambient tenant the default. */
  private void callerOfDefaultAnd(String other) {
    assertThat(other).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private static String labelOf(String tenantId) {
    return LABEL_PREFIX + tenantId;
  }

  /** Copies the tenant's built-in manual contract under {@link #contractId}, labelled by tenant. */
  private void cloneContractInto(String tenantId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<String> columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_name = 'injectors_contracts' AND table_schema = current_schema()",
            String.class);
    Set<String> replaced =
        Set.of(
            "injector_contract_id",
            "injector_contract_external_id",
            "injector_contract_labels",
            "injector_contract_custom");
    String copied =
        columns.stream().filter(c -> !replaced.contains(c)).collect(Collectors.joining(", "));
    jdbc.update(
        "INSERT INTO injectors_contracts ("
            + copied
            + ", injector_contract_id, injector_contract_external_id, injector_contract_labels,"
            + " injector_contract_custom) SELECT "
            + copied
            + ", ?, NULL, hstore('en', ?), true"
            + " FROM injectors_contracts WHERE injector_contract_id = ? AND tenant_id = ?",
        contractId,
        labelOf(tenantId),
        ManualContract.MANUAL_DEFAULT,
        tenantId);
  }

  private void tagCopyOf(String tenantId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    tagId = "ic-rank-tag-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tag_color, tenant_id) VALUES (?, ?, '#ffffff', ?)",
        tagId,
        tagId,
        tenantId);
    jdbc.update(
        "INSERT INTO injector_contract_tags (injector_contract_id, tenant_id, tag_id)"
            + " VALUES (?, ?, ?)",
        contractId,
        tenantId,
        tagId);
  }

  /** The filters that reach a table or a column of the contract through the subquery operators. */
  enum LinkedFilter {
    ATTACK_PATTERN("injector_contract_attack_patterns"),
    DOMAIN("injector_contract_domains"),
    VULNERABILITY("injector_contract_vulnerabilities"),
    PLATFORM("injector_contract_platforms");

    final String key;

    LinkedFilter(String key) {
      this.key = key;
    }
  }

  /**
   * Gives the tenant's copy of the contract what {@code filter} selects on, and no other copy. The
   * copy of every other tenant carries a value the filter does not select. Returns the value to
   * filter on: the id of the linked row, which is what the equality operator of a link table
   * compares.
   */
  private String linkOnlyTo(String tenantId, LinkedFilter filter) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String suffix = UUID.randomUUID().toString();
    switch (filter) {
      case ATTACK_PATTERN -> {
        String id = "ic-rank-ap-" + suffix;
        String externalId = "T" + Math.abs(suffix.hashCode()) + ".9";
        jdbc.update(
            "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_stix_id,"
                + " attack_pattern_name, attack_pattern_external_id, tenant_id)"
                + " VALUES (?, ?, ?, ?, ?)",
            id,
            "attack-pattern--" + suffix,
            id,
            externalId,
            tenantId);
        createdRows.add(new String[] {"attack_patterns", "attack_pattern_id", id});
        jdbc.update(
            "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id, tenant_id,"
                + " attack_pattern_id) VALUES (?, ?, ?)",
            contractId,
            tenantId,
            id);
        return id;
      }
      case DOMAIN -> {
        String id = "ic-rank-domain-" + suffix;
        jdbc.update(
            "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id)"
                + " VALUES (?, ?, '#ffffff', ?)",
            id,
            id,
            tenantId);
        createdRows.add(new String[] {"domains", "domain_id", id});
        jdbc.update(
            "INSERT INTO injectors_contracts_domains (injector_contract_id, tenant_id, domain_id)"
                + " VALUES (?, ?, ?)",
            contractId,
            tenantId,
            id);
        return id;
      }
      case VULNERABILITY -> {
        String id = "ic-rank-vuln-" + suffix;
        String externalId = "CVE-2099-" + Math.abs(suffix.hashCode());
        jdbc.update(
            "INSERT INTO vulnerabilities (vulnerability_id, vulnerability_external_id,"
                + " vulnerability_source_identifier, tenant_id) VALUES (?, ?, ?, ?)",
            id,
            externalId,
            "ic-rank",
            tenantId);
        createdRows.add(new String[] {"vulnerabilities", "vulnerability_id", id});
        jdbc.update(
            "INSERT INTO injectors_contracts_vulnerabilities (injector_contract_id, tenant_id,"
                + " vulnerability_id) VALUES (?, ?, ?)",
            contractId,
            tenantId,
            id);
        return id;
      }
      case PLATFORM -> {
        jdbc.update(
            "UPDATE injectors_contracts SET injector_contract_platforms = '{Linux}'::text[]"
                + " WHERE injector_contract_id = ? AND tenant_id = ?",
            contractId,
            tenantId);
        jdbc.update(
            "UPDATE injectors_contracts SET injector_contract_platforms = '{Windows}'::text[]"
                + " WHERE injector_contract_id = ? AND tenant_id <> ?",
            contractId,
            tenantId);
        return "Linux";
      }
    }
    throw new IllegalStateException(filter.name());
  }

  private InjectorContractSearchPaginationInput search(String textSearch) {
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setSize(1000);
    input.setTextSearch(textSearch);
    input.setIncludeFullDetails(true);
    return input;
  }

  private String postJson(String uri, Object body) throws Exception {
    MvcResult result =
        mvc.perform(
                post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(body))
                    .with(csrf()))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result.getResponse().getContentAsString();
  }

  @Nested
  @DisplayName("Search with no selector")
  class SearchWithNoSelector {

    @Test
    @DisplayName("given_copiesLabelledDifferently_should_searchTheDefaultTenantsCopyTheGetServes")
    void given_copiesLabelledDifferently_should_searchTheDefaultTenantsCopyTheGetServes()
        throws Exception {
      // Arrange: the other tenant sorts below the default one, so the lowest id is not the default
      String lower = tenantOnTheSideOfDefault(true);
      callerOfDefaultAnd(lower);
      contractId = "ic-rank-" + UUID.randomUUID();
      cloneContractInto(lower);
      cloneContractInto(Tenant.DEFAULT_TENANT_UUID);

      // Act
      String full = postJson(INJECTOR_CONTRACT_URL + "/search", search(LABEL_PREFIX));
      String arsenal = postJson(THREAT_ARSENAL_URL + "/search", search(LABEL_PREFIX));
      String byId =
          mvc.perform(get(INJECTOR_CONTRACT_URL + "/" + contractId))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String expected = labelOf(Tenant.DEFAULT_TENANT_UUID);
      assertThat((List<String>) JsonPath.read(full, "$.content[*].injector_contract_labels.en"))
          .containsExactly(expected);
      assertThat((List<String>) JsonPath.read(arsenal, "$.content[*].action_labels.en"))
          .containsExactly(expected);
      assertThat((String) JsonPath.read(byId, "$.injector_contract_labels.en")).isEqualTo(expected);
    }

    @Test
    @DisplayName("given_filterOnlyTheNonDefaultCopyMatches_should_listTheContractOnce")
    void given_filterOnlyTheNonDefaultCopyMatches_should_listTheContractOnce() throws Exception {
      // Arrange: the other tenant sorts above the default one, and only its copy carries the tag
      String higher = tenantOnTheSideOfDefault(false);
      callerOfDefaultAnd(higher);
      contractId = "ic-rank-" + UUID.randomUUID();
      cloneContractInto(higher);
      cloneContractInto(Tenant.DEFAULT_TENANT_UUID);
      tagCopyOf(higher);
      InjectorContractSearchPaginationInput input = search(LABEL_PREFIX);
      input.setFilterGroup(
          Filters.FilterGroup.builder()
              .mode(Filters.FilterMode.and)
              .filters(
                  List.of(
                      Filters.Filter.getNewDefaultEqualFilter(
                          "injector_contract_tags", List.of(tagId))))
              .build());

      // Act
      String body = postJson(INJECTOR_CONTRACT_URL + "/search", input);

      // Assert: the copy kept is the best-ranked one among those matching the filter
      assertThat((List<String>) JsonPath.read(body, "$.content[*].injector_contract_labels.en"))
          .containsExactly(labelOf(higher));
      assertThat(((Number) JsonPath.read(body, "$.totalElements")).longValue()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkedFilter.class)
    @DisplayName("given_filterOnlyTheNonDefaultCopyMatches_should_listTheContractOnceThroughIt")
    void given_filterOnlyTheNonDefaultCopyMatches_should_listTheContractOnceThroughIt(
        LinkedFilter filter) throws Exception {
      // Arrange: the other tenant sorts above the default one, and only its copy matches
      String higher = tenantOnTheSideOfDefault(false);
      callerOfDefaultAnd(higher);
      contractId = "ic-rank-" + UUID.randomUUID();
      cloneContractInto(higher);
      cloneContractInto(Tenant.DEFAULT_TENANT_UUID);
      String value = linkOnlyTo(higher, filter);
      InjectorContractSearchPaginationInput input = search(LABEL_PREFIX);
      input.setFilterGroup(
          Filters.FilterGroup.builder()
              .mode(Filters.FilterMode.and)
              .filters(List.of(Filters.Filter.getNewDefaultEqualFilter(filter.key, List.of(value))))
              .build());

      // Act
      String body = postJson(INJECTOR_CONTRACT_URL + "/search", input);

      // Assert
      assertThat((List<String>) JsonPath.read(body, "$.content[*].injector_contract_labels.en"))
          .containsExactly(labelOf(higher));
      assertThat(((Number) JsonPath.read(body, "$.totalElements")).longValue()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Threat arsenal CSV export with no selector")
  class ExportWithNoSelector {

    private List<String> dataRows(String csv) {
      List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();
      return lines.subList(1, lines.size());
    }

    private String exportCsv(String textSearch) throws Exception {
      MvcResult result =
          mvc.perform(
                  post("/api/mappers/export/csv?csvType=" + CsvType.INJECTOR_CONTRACTS)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(search(textSearch)))
                      .with(csrf()))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("given_builtinContractInTwoTenants_should_exportOneRowCountedLikeTheSearch")
    void given_builtinContractInTwoTenants_should_exportOneRowCountedLikeTheSearch()
        throws Exception {
      // Arrange
      String other = tenantOnTheSideOfDefault(true);
      callerOfDefaultAnd(other);

      // Act
      String csv = exportCsv("Manual");
      String searched = postJson(THREAT_ARSENAL_URL + "/search", search("Manual"));

      // Assert
      long total = ((Number) JsonPath.read(searched, "$.totalElements")).longValue();
      assertThat(total).isEqualTo(1);
      assertThat(dataRows(csv)).hasSize((int) total);
    }

    @Test
    @DisplayName("given_copiesLabelledDifferently_should_exportTheDefaultTenantsCopy")
    void given_copiesLabelledDifferently_should_exportTheDefaultTenantsCopy() throws Exception {
      // Arrange
      String other = tenantOnTheSideOfDefault(true);
      callerOfDefaultAnd(other);
      contractId = "ic-rank-" + UUID.randomUUID();
      cloneContractInto(other);
      cloneContractInto(Tenant.DEFAULT_TENANT_UUID);

      // Act
      List<String> rows = dataRows(exportCsv(LABEL_PREFIX));

      // Assert
      assertThat(rows).hasSize(1);
      assertThat(rows.getFirst()).contains(labelOf(Tenant.DEFAULT_TENANT_UUID));
    }
  }
}
