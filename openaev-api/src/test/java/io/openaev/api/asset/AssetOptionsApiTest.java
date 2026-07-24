package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Asset options API tests")
class AssetOptionsApiTest extends IntegrationTest {

  // Shared prefix so the name search only ever matches the data seeded by this test class.
  private static final String NAME_PREFIX = "AssetOptionsApiTest-";

  @Autowired private MockMvc mvc;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;

  private Endpoint endpoint1;
  private Endpoint endpoint2;
  private SecurityPlatform securityPlatform;

  /**
   * Seeds two endpoints and one security platform sharing the same name prefix. Called from inside
   * each test (not @BeforeEach) so the mock user's tenant context is active when entities persist.
   */
  private void seedInventory() {
    Endpoint e1 = createEndpoint();
    e1.setName(NAME_PREFIX + "endpoint-1");
    endpoint1 = endpointRepository.save(e1);
    Endpoint e2 = createEndpoint();
    e2.setName(NAME_PREFIX + "endpoint-2");
    endpoint2 = endpointRepository.save(e2);
    securityPlatform =
        securityPlatformRepository.save(
            SecurityPlatformFixture.createDefault(NAME_PREFIX + "platform", "EDR"));
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("GET /api/assets/options")
  class OptionsByName {

    @Test
    @DisplayName("Returns matching assets but never security platforms")
    void given_searchText_should_returnMatchingAssetsWithoutSecurityPlatforms() throws Exception {
      seedInventory();

      String response =
          mvc.perform(
                  get(ASSET_URI + "/options")
                      .queryParam("searchText", NAME_PREFIX)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids)
          .containsExactlyInAnyOrder(endpoint1.getId(), endpoint2.getId())
          .doesNotContain(securityPlatform.getId());
    }

    @Test
    @DisplayName("Narrower search text only returns the matching asset")
    void given_narrowerSearchText_should_returnOnlyMatchingAsset() throws Exception {
      seedInventory();

      String response =
          mvc.perform(
                  get(ASSET_URI + "/options")
                      .queryParam("searchText", NAME_PREFIX + "endpoint-2")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      List<String> labels = JsonPath.read(response, "$[*].label");
      assertThat(ids).containsExactly(endpoint2.getId());
      assertThat(labels).containsExactly(NAME_PREFIX + "endpoint-2");
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("POST /api/assets/options")
  class OptionsByIds {

    @Test
    @DisplayName("Resolves labels for the given asset ids")
    void given_assetIds_should_resolveLabels() throws Exception {
      seedInventory();

      String response =
          mvc.perform(
                  post(ASSET_URI + "/options")
                      .content(asJsonString(List.of(endpoint1.getId(), endpoint2.getId())))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactlyInAnyOrder(endpoint1.getId(), endpoint2.getId());
    }

    @Test
    @DisplayName("Excludes security platform ids, consistent with the GET endpoint")
    void given_securityPlatformId_should_excludeItFromResolvedOptions() throws Exception {
      seedInventory();

      String response =
          mvc.perform(
                  post(ASSET_URI + "/options")
                      .content(asJsonString(List.of(endpoint1.getId(), securityPlatform.getId())))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).containsExactly(endpoint1.getId());
    }

    @Test
    @DisplayName("A missing request body resolves to an empty list")
    void given_noBody_should_returnEmptyList() throws Exception {
      String response =
          mvc.perform(
                  post(ASSET_URI + "/options")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("An empty id list resolves to an empty list")
    void given_emptyIdList_should_returnEmptyList() throws Exception {
      String response =
          mvc.perform(
                  post(ASSET_URI + "/options")
                      .content(asJsonString(List.of()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("Unknown ids resolve to an empty list")
    void given_unknownIds_should_returnEmptyList() throws Exception {
      String response =
          mvc.perform(
                  post(ASSET_URI + "/options")
                      .content(asJsonString(List.of("00000000-0000-0000-0000-000000000000")))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$[*].id");
      assertThat(ids).isEmpty();
    }
  }
}
