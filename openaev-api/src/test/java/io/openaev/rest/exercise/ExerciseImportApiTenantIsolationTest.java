package io.openaev.rest.exercise;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.form.InjectsImportInput;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.InjectImportService;
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
 * Companion to {@link io.openaev.rest.scenario.ScenarioImportApiTenantIsolationTest} for the
 * exercise import endpoints: with {@code import_mappers} activated, a caller may only use a mapper
 * its tenant can see. The mapper {@code findById} is the real, scoped read; the downstream xls
 * processing and the exercise lookup are stubbed so the positive case can get past the lookup.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("ExerciseImportApi scopes the mapper lookup to the caller's tenants")
class ExerciseImportApiTenantIsolationTest extends IntegrationTest {

  private static final String DRY_RUN =
      "/api/tenants/{tenantId}/exercises/{exerciseId}/xls/{importId}/dry";
  private static final String IMPORT =
      "/api/tenants/{tenantId}/exercises/{exerciseId}/xls/{importId}/import";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @MockitoBean private InjectImportService injectImportService;
  @MockitoBean private ExerciseService exerciseService;

  private String tenantA;
  private String mapperA;
  private String mapperB;

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ex-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("ex-iso-b").getId();
    mapperA = seedMapper(tenantA, "ex-mapper-a");
    mapperB = seedMapper(tenantB, "ex-mapper-b");
    when(exerciseService.exercise(any())).thenReturn(new Exercise());
    when(injectImportService.importInjectIntoExerciseFromXLS(
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
