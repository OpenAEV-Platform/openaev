package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MitigationInputFixture.createMitigationInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.mapper.form.ImportMapperAddInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant of a created import mapper, mitigation or cwe is the one the request selects through
 * the {@code X-Tenant-Ids} header, or the caller's only tenant when it sends no selector (the
 * tenant path is covered by each table's isolation test). The thread-local is cleared and its
 * fallback asserted before every request: a creator that left the tenant to it is refused or files
 * the row under the default tenant, and either fails here.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A create is attributed to the tenant the request selects, on every route")
class ExplicitTenantAttributionRoutesTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String MAPPERS = "/api/mappers";
  private static final String MITIGATIONS = "/api/mitigations";
  private static final String VULNERABILITIES = "/api/vulnerabilities";
  private static final String CWE_EXTERNAL_ID = "CWE-79";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private void dropAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private String perform(String uri, String selectedTenant, String body, String... pathArgs)
      throws Exception {
    var request =
        post(uri, (Object[]) pathArgs)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf());
    if (selectedTenant != null) {
      request = request.header(TENANT_HEADER, selectedTenant);
    }
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String storedTenant(String table, String idColumn, String id) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT tenant_id FROM " + table + " WHERE CAST(" + idColumn + " AS text) = ?1")
            .setParameter(1, id)
            .getSingleResult();
  }

  private String createMapper(String uri, String selectedTenant, String... pathArgs)
      throws Exception {
    ImportMapperAddInput input = new ImportMapperAddInput();
    input.setName("attributed-mapper");
    input.setInjectTypeColumn("A");
    String response = perform(uri, selectedTenant, asJsonString(input), pathArgs);
    return storedTenant(
        "import_mappers", "mapper_id", JsonPath.read(response, "$.import_mapper_id"));
  }

  private String createMitigation(String uri, String selectedTenant, String... pathArgs)
      throws Exception {
    String response =
        perform(
            uri,
            selectedTenant,
            asJsonString(createMitigationInput("attributed-mitigation", "M9930")),
            pathArgs);
    return storedTenant("mitigations", "mitigation_id", JsonPath.read(response, "$.mitigation_id"));
  }

  private String createCwe(String uri, String selectedTenant, String... pathArgs) throws Exception {
    String body =
        """
        {"vulnerability_external_id":"CVE-ATTRIBUTED-1","vulnerability_cvss_v31":4.5,
         "vulnerability_cwes":[{"cwe_external_id":"%s"}]}
        """
            .formatted(CWE_EXTERNAL_ID);
    perform(uri, selectedTenant, body, pathArgs);
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM cwes WHERE cwe_external_id = ?1")
            .setParameter(1, CWE_EXTERNAL_ID)
            .getResultList()
            .getFirst();
  }

  @Nested
  @DisplayName("a caller of two tenants selecting the second through the X-Tenant-Ids header")
  class HeaderRoute {

    private String tenantB;

    private void twoTenants() throws Exception {
      tenantHelper.createTenantWithCurrentUser("attr-route-a");
      tenantB = tenantHelper.createTenantWithCurrentUser("attr-route-b").getId();
      dropAmbientTenant();
    }

    @Test
    @DisplayName("given a mapper create, should attribute it to the selected tenant")
    void given_mapperCreate_should_attributeItToTheSelectedTenant() throws Exception {
      // Arrange
      twoTenants();

      // Act
      String tenant = createMapper(MAPPERS, tenantB);

      // Assert
      assertThat(tenant).isEqualTo(tenantB);
    }

    @Test
    @DisplayName("given a mitigation create, should attribute it to the selected tenant")
    void given_mitigationCreate_should_attributeItToTheSelectedTenant() throws Exception {
      // Arrange
      twoTenants();

      // Act
      String tenant = createMitigation(MITIGATIONS, tenantB);

      // Assert
      assertThat(tenant).isEqualTo(tenantB);
    }

    @Test
    @DisplayName("given a vulnerability create carrying a cwe, should attribute the cwe")
    void given_vulnerabilityCreateWithCwe_should_attributeTheCweToTheSelectedTenant()
        throws Exception {
      // Arrange
      twoTenants();

      // Act
      String tenant = createCwe(VULNERABILITIES, tenantB);

      // Assert
      assertThat(tenant).isEqualTo(tenantB);
    }
  }

  @Nested
  @DisplayName("a caller of a single tenant with no selector on the non-prefixed route")
  class NoSelectorRoute {

    private String onlyTenant;

    private void oneTenant() throws Exception {
      onlyTenant = tenantHelper.createTenantWithCurrentUser("attr-route-only").getId();
      dropAmbientTenant();
    }

    @Test
    @DisplayName("given a mapper create, should attribute it to the only tenant")
    void given_mapperCreate_should_attributeItToTheOnlyTenant() throws Exception {
      // Arrange
      oneTenant();

      // Act
      String tenant = createMapper(MAPPERS, null);

      // Assert
      assertThat(tenant).isEqualTo(onlyTenant);
    }

    @Test
    @DisplayName("given a mitigation create, should attribute it to the only tenant")
    void given_mitigationCreate_should_attributeItToTheOnlyTenant() throws Exception {
      // Arrange
      oneTenant();

      // Act
      String tenant = createMitigation(MITIGATIONS, null);

      // Assert
      assertThat(tenant).isEqualTo(onlyTenant);
    }

    @Test
    @DisplayName("given a vulnerability create carrying a cwe, should attribute the cwe")
    void given_vulnerabilityCreateWithCwe_should_attributeTheCweToTheOnlyTenant() throws Exception {
      // Arrange
      oneTenant();

      // Act
      String tenant = createCwe(VULNERABILITIES, null);

      // Assert
      assertThat(tenant).isEqualTo(onlyTenant);
    }
  }
}
