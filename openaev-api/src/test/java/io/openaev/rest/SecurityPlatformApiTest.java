package io.openaev.rest;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class SecurityPlatformApiTest extends IntegrationTest {

  private static final String SECURITY_PLATFORM_NAME = "My Security Platform ";

  @Autowired private MockMvc mvc;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @BeforeEach
  public void beforeEach() {
    securityPlatformComposer.reset();
  }

  @DisplayName("Test create SecurityPlatform")
  @Test
  @WithMockUser(isAdmin = true)
  void createSecurityPlatformShouldSucceed() throws Exception {
    SecurityPlatformInput input = new SecurityPlatformInput();
    input.setName("PlatformA");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);

    mvc.perform(
            post(SECURITY_PLATFORM_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_name").value("PlatformA"));
  }

  @DisplayName("Test create duplicate SecurityPlatform fails")
  @Test
  @WithMockUser(isAdmin = true)
  void createDuplicateSecurityPlatformShouldFail() throws Exception {
    SecurityPlatformInput input = new SecurityPlatformInput();
    input.setName("PlatformB");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);

    securityPlatformComposer
        .forSecurityPlatform(
            SecurityPlatformFixture.createDefault(
                "PlatformB", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.toString()))
        .persist();
    entityManager.flush();
    entityManager.clear();

    // Second creation with same name and type should fail
    mvc.perform(
        post(SECURITY_PLATFORM_URI)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));

    assertThatThrownBy(() -> entityManager.flush())
        .hasMessageContaining("unique_security_platform_name_type_ci_idx");
  }

  @DisplayName("Test update SecurityPlatform to duplicate name/type fails")
  @Test
  @WithMockUser(isAdmin = true)
  void updateSecurityPlatformToDuplicateShouldFail() throws Exception {
    // Create first platform
    securityPlatformComposer
        .forSecurityPlatform(
            SecurityPlatformFixture.createDefault(
                "PlatformC", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()))
        .persist();

    // Create second platform
    SecurityPlatform saved =
        securityPlatformComposer
            .forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "PlatformD", SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()))
            .persist()
            .get();

    // Prepare update input that duplicates name/type
    SecurityPlatformInput input = new SecurityPlatformInput();
    input.setName("PlatformC");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);

    // Perform update and assert constraint violation
    assertThatThrownBy(
            () ->
                mvc.perform(
                        put(SECURITY_PLATFORM_URI + "/" + saved.getId())
                            .content(asJsonString(input))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(csrf()))
                    .andDo(result -> entityManager.flush())
                    .andReturn())
        .hasMessageContaining("unique_security_platform_name_type_ci_idx");
  }

  @DisplayName("Test update SecurityPlatform to new name succeeds")
  @Test
  @WithMockUser(isAdmin = true)
  void updateSecurityPlatformToNewNameShouldSucceed() throws Exception {
    SecurityPlatformInput input = new SecurityPlatformInput();
    input.setName("PlatformE");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);

    String id =
        mvc.perform(
                post(SECURITY_PLATFORM_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    entityManager.flush();
    entityManager.clear();

    String spId = JsonPath.read(id, "$.asset_id");

    input.setName("PlatformE-Updated");
    mvc.perform(
            put(SECURITY_PLATFORM_URI + "/" + spId)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_name").value("PlatformE-Updated"));
  }

  // Options tests

  @DisplayName("Test optionsByName")
  @ParameterizedTest
  @MethodSource("optionsByNameTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByNameTest(String searchText, Integer expectedNumberOfResults) throws Exception {
    // --PREPARE--
    prepareOptionsSecurityPlatformTestData();

    // --EXECUTE--;
    String response =
        mvc.perform(
                get(SECURITY_PLATFORM_URI + "/options")
                    .queryParam("searchText", searchText)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  @DisplayName("Test optionsById")
  @ParameterizedTest
  @MethodSource("optionsByIdTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByIdTest(Integer numberOfSecurityPlatformToProvide, Integer expectedNumberOfResults)
      throws Exception {
    List<SecurityPlatformComposer.Composer> securityPlatforms =
        prepareOptionsSecurityPlatformTestData();

    List<String> securityPlatformIdsToSearch = new ArrayList<>();
    for (int i = 0; i < numberOfSecurityPlatformToProvide; i++) {
      securityPlatformIdsToSearch.add(securityPlatforms.get(i).get().getId());
    }

    // --EXECUTE--;
    String response =
        mvc.perform(
                post(SECURITY_PLATFORM_URI + "/options")
                    .content(asJsonString(securityPlatformIdsToSearch))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  private List<SecurityPlatformComposer.Composer> prepareOptionsSecurityPlatformTestData() {
    SecurityPlatform securityPlatformTest1 = new SecurityPlatform();
    securityPlatformTest1.setName(SECURITY_PLATFORM_NAME + "1");
    securityPlatformTest1.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
    SecurityPlatformComposer.Composer securityPlatform1 =
        securityPlatformComposer.forSecurityPlatform(securityPlatformTest1);
    securityPlatform1.persist();
    SecurityPlatform securityPlatformTest2 = new SecurityPlatform();
    securityPlatformTest2.setName(SECURITY_PLATFORM_NAME + "2");
    securityPlatformTest2.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
    SecurityPlatformComposer.Composer securityPlatform2 =
        securityPlatformComposer.forSecurityPlatform(securityPlatformTest2);
    securityPlatform2.persist();
    entityManager.flush();
    entityManager.clear();
    return List.of(securityPlatform1, securityPlatform2);
  }

  Stream<Arguments> optionsByIdTestParameters() {
    return Stream.of(
        Arguments.of(0, 0), // Case 1: 0 ID given
        Arguments.of(1, 1), // Case 1: 1 ID given
        Arguments.of(2, 2) // Case 2: 2 IDs given
        );
  }

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of("toto", 0),
        Arguments.of(SECURITY_PLATFORM_NAME, 2),
        Arguments.of(SECURITY_PLATFORM_NAME + "1", 1));
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    private SecurityPlatform createTenantSecurityPlatform(String tenantId, String name)
        throws Exception {
      SecurityPlatformInput input = new SecurityPlatformInput();
      input.setName(name);
      input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/security_platforms")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String platformId = JsonPath.read(createResponse, "$.asset_id");
      return securityPlatformRepository.findByIdAndTenantId(platformId, tenantId).orElseThrow();
    }

    @Test
    @DisplayName("SecurityPlatform created in tenant X should NOT be readable from tenant Y")
    void given_securityPlatformInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.ACCESS_SECURITY_PLATFORMS, Capability.MANAGE_SECURITY_PLATFORMS));
      Tenant tenantY =
          tenantHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_SECURITY_PLATFORMS));

      SecurityPlatform platformX =
          createTenantSecurityPlatform(tenantX.getId(), "Isolation Read Platform");
      entityManager.flush();
      entityManager.clear();

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/"
                          + tenantY.getId()
                          + "/security_platforms/"
                          + platformX.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      Assertions.assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("SecurityPlatform created in tenant X should be readable from tenant X")
    void given_securityPlatformInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.ACCESS_SECURITY_PLATFORMS, Capability.MANAGE_SECURITY_PLATFORMS));

      SecurityPlatform platformX =
          createTenantSecurityPlatform(tenantX.getId(), "Same Tenant Platform");

      // -------- Act & Assert --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/security_platforms/" + platformX.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SecurityPlatform created in tenant X should NOT be updatable from tenant Y")
    void given_securityPlatformInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.ACCESS_SECURITY_PLATFORMS, Capability.MANAGE_SECURITY_PLATFORMS));
      Tenant tenantY =
          tenantHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(Capability.ACCESS_SECURITY_PLATFORMS, Capability.MANAGE_SECURITY_PLATFORMS));

      SecurityPlatform platformX =
          createTenantSecurityPlatform(tenantX.getId(), "Update Isolation Platform");
      entityManager.flush();
      entityManager.clear();

      SecurityPlatformInput updateInput = new SecurityPlatformInput();
      updateInput.setName("Hijacked Platform");
      updateInput.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantY.getId()
                          + "/security_platforms/"
                          + platformX.getId())
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      Assertions.assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
