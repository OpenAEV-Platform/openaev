package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_ID;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_NAME;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("ArticleBehavior")
class ArticleBehaviorTest extends IntegrationTest {

  @Autowired private ArticleBehavior articleBehavior;
  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  @BeforeEach
  void setUp() {
    teamComposer.reset();
    userComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given article expectation should return true")
    void given_article_expectation_should_return_true() {
      // Arrange
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();

      // Act
      boolean supported = articleBehavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given non article expectation should return false")
    void given_non_article_expectation_should_return_false() {
      // Arrange
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();

      // Act
      boolean supported = articleBehavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("initializeAndSaveInjectExpectationsFromExecutableInject")
  class InitializeAndSaveInjectExpectation {

    @Test
    @DisplayName(
        "given one team with one player should create result matching buildDefaultForMediaPressure values")
    void given_one_team_with_one_player_should_create_result_matching_media_pressure_defaults() {
      // Arrange
      Team team =
          teamComposer
              .forTeam(TeamFixture.getDefaultContextualTeam())
              .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()))
              .persist()
              .get();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer.forInjectorContract(
                      InjectorContractFixture.createDefaultInjectorContract()))
              .withTeam(teamComposer.forTeam(team))
              .persist()
              .get();

      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, inject, List.of(team), List.of(), List.of(), List.of());

      ArticleInjectExpectation template = new ArticleInjectExpectation();
      template.setInject(inject);
      template.setExpectedScore(100.0);
      template.setExpirationTime(21600L);

      // Act
      articleBehavior.initializeAndSaveInjectExpectationsFromExecutableInject(
          executableInject, template, null, null);
      entityManager.flush();

      // Assert
      List<BaseInjectExpectation> saved =
          injectExpectationRepository.findAllByInjectId(inject.getId());

      // Player-level expectation should have one result matching buildDefaultForMediaPressure
      TableTopInjectExpectation playerExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() != null)
              .findFirst()
              .orElseThrow();
      assertThat(playerExpectation.getResults()).hasSize(1);

      InjectExpectationResult result = playerExpectation.getResults().getFirst();
      assertThat(result.getSourceId()).isEqualTo(MEDIA_PRESSURE_SOURCE_ID);
      assertThat(result.getSourceType()).isEqualTo(MEDIA_PRESSURE_SOURCE_TYPE);
      assertThat(result.getSourceName()).isEqualTo(MEDIA_PRESSURE_SOURCE_NAME);
      assertThat(result.getResult()).isNull();
      assertThat(result.getScore()).isNull();
      assertThat(result.getDate()).isNotBlank();

      // Team-level expectation should have no results
      TableTopInjectExpectation teamExpectation =
          saved.stream()
              .map(TableTopInjectExpectation.class::cast)
              .filter(e -> e.getUser() == null)
              .findFirst()
              .orElseThrow();
      assertThat(teamExpectation.getResults()).isEmpty();
    }
  }
}
