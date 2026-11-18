package io.openaev.rest.channel;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ArticleFixture.ARTICLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Article;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.rest.channel.form.ArticleUpdateInput;
import io.openaev.utils.fixtures.ArticleFixture;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ArticleComposer;
import io.openaev.utils.fixtures.composers.ChannelComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
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
@WithMockUser(isAdmin = true)
@DisplayName("Articles reached through their simulation or scenario")
class ChannelArticleParentTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ArticleComposer articleComposer;
  @Autowired private ChannelComposer channelComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private ArticleComposer.Composer newArticle() {
    return articleComposer
        .forArticle(ArticleFixture.getArticleNoChannel())
        .withChannel(channelComposer.forChannel(ChannelFixture.getChannel()));
  }

  private String updateInput(Article article) {
    ArticleUpdateInput input = new ArticleUpdateInput();
    input.setName("Updated article");
    input.setChannelId(article.getChannel().getId());
    return asJsonString(input);
  }

  @Nested
  @DisplayName("Simulation")
  class Simulation {

    private Article persistArticleInNewSimulation() {
      ArticleComposer.Composer article = newArticle();
      ExerciseComposer.Composer simulation =
          exerciseComposer.forExercise(ExerciseFixture.getExercise()).withArticle(article);
      article.get().setExercise(simulation.get());
      simulation.persist();
      return article.get();
    }

    private String otherSimulationId() {
      return exerciseComposer.forExercise(ExerciseFixture.getExercise()).persist().get().getId();
    }

    @Test
    @DisplayName("Given an article of another simulation, should not update it")
    void given_articleOfAnotherSimulation_should_notUpdateIt() throws Exception {
      // Arrange
      Article article = persistArticleInNewSimulation();
      String otherSimulationId = otherSimulationId();

      // Act
      mvc.perform(
              put(EXERCISE_URI + "/" + otherSimulationId + "/articles/" + article.getId())
                  .content(updateInput(article))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertEquals(
          ARTICLE_NAME, articleRepository.findById(article.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("Given an article of another simulation, should not delete it")
    void given_articleOfAnotherSimulation_should_notDeleteIt() throws Exception {
      // Arrange
      Article article = persistArticleInNewSimulation();
      String otherSimulationId = otherSimulationId();

      // Act
      mvc.perform(
              delete(EXERCISE_URI + "/" + otherSimulationId + "/articles/" + article.getId())
                  .with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertTrue(articleRepository.findById(article.getId()).isPresent());
    }

    @Test
    @DisplayName("Given an article of the simulation, should update it")
    void given_articleOfTheSimulation_should_updateIt() throws Exception {
      // Arrange
      Article article = persistArticleInNewSimulation();

      // Act
      mvc.perform(
              put(EXERCISE_URI
                      + "/"
                      + article.getExercise().getId()
                      + "/articles/"
                      + article.getId())
                  .content(updateInput(article))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // Assert
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Scenario")
  class Scenario {

    private Article persistArticleInNewScenario() {
      ArticleComposer.Composer article = newArticle();
      scenarioComposer.forScenario(ScenarioFixture.getScenario()).withArticle(article).persist();
      return article.get();
    }

    private String otherScenarioId() {
      return scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get().getId();
    }

    @Test
    @DisplayName("Given an article of another scenario, should not update it")
    void given_articleOfAnotherScenario_should_notUpdateIt() throws Exception {
      // Arrange
      Article article = persistArticleInNewScenario();
      String otherScenarioId = otherScenarioId();

      // Act
      mvc.perform(
              put(SCENARIO_URI + "/" + otherScenarioId + "/articles/" + article.getId())
                  .content(updateInput(article))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertEquals(
          ARTICLE_NAME, articleRepository.findById(article.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("Given an article of another scenario, should not delete it")
    void given_articleOfAnotherScenario_should_notDeleteIt() throws Exception {
      // Arrange
      Article article = persistArticleInNewScenario();
      String otherScenarioId = otherScenarioId();

      // Act
      mvc.perform(
              delete(SCENARIO_URI + "/" + otherScenarioId + "/articles/" + article.getId())
                  .with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertTrue(articleRepository.findById(article.getId()).isPresent());
    }
  }
}
