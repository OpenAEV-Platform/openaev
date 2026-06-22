package io.openaev.api.attack_pattern;

import static io.openaev.api.attack_pattern.AttackPatternCoverageApi.ATTACK_PATTERN_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
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
@DisplayName("Global ATT&CK coverage API integration tests")
public class AttackPatternCoverageApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Nested
  @DisplayName("When fetching the global ATT&CK coverage")
  @WithMockUser(isAdmin = true)
  class GetGlobalCoverage {

    @Test
    @DisplayName("Should return the coverage as a JSON array")
    void given_noQueryParameter_should_returnCoverageArray() throws Exception {
      // Act
      String response =
          mvc.perform(
                  get(ATTACK_PATTERN_URI + "/coverage")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      // The coverage is computed from Elasticsearch expectation-inject docs; an empty array is
      // acceptable when the test environment has no indexed expectation data.
      assertThatJson(response).isArray();
    }

    @Test
    @DisplayName("Should accept the latest scoping query parameter")
    void given_latestQueryParameter_should_returnCoverageArray() throws Exception {
      // Act
      String response =
          mvc.perform(
                  get(ATTACK_PATTERN_URI + "/coverage?latest=5")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).isArray();
    }
  }
}
