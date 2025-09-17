package io.openbas.rest;

import static io.openbas.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openbas.IntegrationTest;
import io.openbas.database.model.SecurityPlatform;
import io.openbas.database.repository.SecurityPlatformRepository;
import io.openbas.utils.fixtures.composers.SecurityPlatformComposer;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  public void beforeEach() {
    securityPlatformComposer.reset();
  }

  // Options tests

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

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of("toto", 0),
        Arguments.of(SECURITY_PLATFORM_NAME, 2),
        Arguments.of(SECURITY_PLATFORM_NAME + "1", 1));
  }

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

  Stream<Arguments> optionsByIdTestParameters() {
    return Stream.of(
        Arguments.of(0, 0), // Case 1: 0 ID given
        Arguments.of(1, 1), // Case 1: 1 ID given
        Arguments.of(2, 2) // Case 2: 2 IDs given
        );
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
}
