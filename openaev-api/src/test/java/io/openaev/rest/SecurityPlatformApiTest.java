package io.openaev.rest;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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
                .accept(MediaType.APPLICATION_JSON))
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
            .accept(MediaType.APPLICATION_JSON));

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
                            .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
}
