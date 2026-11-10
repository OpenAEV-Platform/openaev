package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Scenario author")
class ScenarioAuthorApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioRepository scenarioRepository;

  private String authorOf(String scenarioId) {
    User author = scenarioRepository.findById(scenarioId).orElseThrow().getAuthorUser();
    return author == null ? null : author.getId();
  }

  private String body(ResultActions result) throws Exception {
    return result
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("A scenario a user creates is theirs, and never shows the author")
  void given_scenarioCreatedByAUser_should_beAuthoredByThem() throws Exception {
    // Arrange
    ScenarioInput input = new ScenarioInput();
    input.setName("Authored scenario");

    // Act
    String scenarioId =
        JsonPath.read(
            body(
                mvc.perform(
                    post(SCENARIO_URI)
                        .content(asJsonString(input))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))),
            "$.scenario_id");
    String detail = body(mvc.perform(get(SCENARIO_URI + "/" + scenarioId).with(csrf())));

    // Assert
    assertThat(authorOf(scenarioId)).isNotNull();
    assertThat(detail).doesNotContain("author");
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("A duplicate is authored by the user who duplicates")
  void given_duplicatedScenario_should_beAuthoredByTheDuplicatingUser() throws Exception {
    // Arrange
    Scenario origin = ScenarioFixture.createDefaultCrisisScenario();
    origin.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    String originId = scenarioRepository.save(origin).getId();

    // Act
    String duplicateId =
        JsonPath.read(
            body(mvc.perform(post(SCENARIO_URI + "/" + originId).with(csrf()))), "$.scenario_id");

    // Assert
    assertThat(authorOf(originId)).isNull();
    assertThat(authorOf(duplicateId)).isNotNull();
  }
}
