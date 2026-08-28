package io.openaev.rest.scenario;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.rest.scenario.form.InjectsImportInput;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.InjectImportService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * With {@code import_mappers} activated, the import endpoints must only let a caller use a mapper
 * its tenant can see. The mapper lookup ({@code findById}) is the real, scoped read here; the
 * downstream xls processing is not the subject and is stubbed so the positive case can get past the
 * lookup without a stored file. The scenario lookup is stubbed for the same reason.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("ScenarioImportApi scopes the mapper lookup to the caller's tenants")
class ScenarioImportApiTenantIsolationTest extends IntegrationTest {

  private static final String DRY_RUN =
      "/api/tenants/{tenantId}/scenarios/{scenarioId}/xls/{importId}/dry";
  private static final String IMPORT =
      "/api/tenants/{tenantId}/scenarios/{scenarioId}/xls/{importId}/import";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @MockitoBean private InjectImportService injectImportService;
  @MockitoBean private ScenarioService scenarioService;

  private String tenantA;
  private String mapperA;
  private String mapperB;

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("scen-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("scen-iso-b").getId();
    mapperA = seedMapper(tenantA, "scen-mapper-a");
    mapperB = seedMapper(tenantB, "scen-mapper-b");
    when(scenarioService.scenario(any())).thenReturn(new Scenario());
    when(injectImportService.importInjectIntoScenarioFromXLS(
            any(), any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new ImportTestSummary());
  }

  @ParameterizedTest
  @ValueSource(strings = {DRY_RUN, IMPORT})
  @DisplayName("under tenant A's path: A's own mapper is found and the import proceeds")
  void underTenantAWithOwnMapper(String url) throws Exception {
    mvc.perform(request(url, tenantA, mapperA)).andExpect(status().is2xxSuccessful());
  }

  @ParameterizedTest
  @ValueSource(strings = {DRY_RUN, IMPORT})
  @DisplayName("under tenant A's path: B's mapper is not found (cross-tenant lookup blocked)")
  void underTenantAWithCrossTenantMapperIsBlocked(String url) throws Exception {
    mvc.perform(request(url, tenantA, mapperB)).andExpect(status().isNotFound());
  }

  private MockHttpServletRequestBuilder request(String url, String tenantId, String mapperId)
      throws Exception {
    InjectsImportInput input = new InjectsImportInput();
    input.setName("iso-test");
    input.setImportMapperId(mapperId);
    input.setTimezoneOffset(0);
    return post(url, tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(asJsonString(input))
        .with(csrf());
  }

  private String seedMapper(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, :col, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("col", "inject_type")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
