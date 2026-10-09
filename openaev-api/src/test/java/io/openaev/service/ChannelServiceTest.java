package io.openaev.service;

import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.MEDIA_PRESSURE_SOURCE_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.ArticleFixture;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ChannelService")
@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String EXERCISE_ID = "exercise-1";
  private static final String CHANNEL_ID = "channel-1";
  private static final String EXPECTATION_ID = "expectation-1";
  private static final Double EXPECTED_SCORE = 100.0;

  @Mock private InjectExpectationService injectExpectationService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private ScenarioService scenarioService;
  @Mock private ArticleRepository articleRepository;
  @Mock private ChannelRepository channelRepository;
  @Spy private ObjectMapper mapper = new ObjectMapper();

  @InjectMocks private ChannelService channelService;

  @Nested
  @DisplayName("Validate articles")
  class ValidateArticles {

    @Test
    @DisplayName("Given unread article expectation should validate it through the behaviors")
    void given_unread_article_expectation_should_validate_it_through_the_behaviors() {
      // Arrange
      User user = buildPlayer();
      ArticleInjectExpectation expectation = arrangePublishedArticleExpectation(user);
      expectation.setResults(null); // null results — previously caused NPE with isEmpty()

      // Act
      assertDoesNotThrow(
          () -> channelService.validateArticles(EXERCISE_ID, CHANNEL_ID, user, TENANT_ID));

      // Assert
      ArgumentCaptor<ExpectationUpdateInput> captor =
          ArgumentCaptor.forClass(ExpectationUpdateInput.class);
      verify(injectExpectationService)
          .updateInjectExpectation(eq(EXPECTATION_ID), captor.capture());
      ExpectationUpdateInput input = captor.getValue();
      assertEquals(MEDIA_PRESSURE_SOURCE_ID, input.getSourceId());
      assertEquals(EXPECTED_SCORE, input.getScore());
    }

    @Test
    @DisplayName("Given already read article expectation should not validate it again")
    void given_already_read_article_expectation_should_not_validate_it_again() {
      // Arrange
      User user = buildPlayer();
      ArticleInjectExpectation expectation = arrangePublishedArticleExpectation(user);
      expectation.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder()
                      .sourceId(MEDIA_PRESSURE_SOURCE_ID)
                      .result(expectation.getSuccessLabel())
                      .score(EXPECTED_SCORE)
                      .build())));

      // Act
      channelService.validateArticles(EXERCISE_ID, CHANNEL_ID, user, TENANT_ID);

      // Assert
      verify(injectExpectationService, never())
          .updateInjectExpectation(any(), any(ExpectationUpdateInput.class));
    }

    private User buildPlayer() {
      User user = UserFixture.getUser();
      user.setId("user-1");
      return user;
    }

    /**
     * Arranges an exercise with one published article and returns the player's expectation on it.
     */
    private ArticleInjectExpectation arrangePublishedArticleExpectation(User user) {
      Channel channel = ChannelFixture.getDefaultChannel();
      channel.setId(CHANNEL_ID);

      Article article = ArticleFixture.getArticle(channel);
      article.setId("article-1");

      InjectorContract contract = new InjectorContract();
      contract.setId(CHANNEL_PUBLISH);

      InjectStatus status = new InjectStatus();
      status.setTrackingSentDate(Instant.now());

      Inject inject = new Inject();
      inject.setId("inject-1");
      inject.setInjectorContract(contract);
      inject.setStatus(status);

      ObjectNode contentNode = mapper.createObjectNode();
      contentNode.putArray("articles").add(article.getId());
      inject.setContent(contentNode);

      ArticleInjectExpectation expectation = new ArticleInjectExpectation();
      expectation.setId(EXPECTATION_ID);
      expectation.setExpectedScore(EXPECTED_SCORE);
      expectation.setArticle(article);
      expectation.setUser(user);
      inject.setExpectations(List.of(expectation));

      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setId(EXERCISE_ID);
      exercise.setInjects(List.of(inject));

      when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(channel));
      // tenantId is resolved by the caller (API layer) and passed explicitly, instead of the
      // service reading TenantContext / calling the unscoped findById.
      when(exerciseRepository.findByIdAndTenantId(eq(EXERCISE_ID), eq(TENANT_ID)))
          .thenReturn(Optional.of(exercise));
      when(articleRepository.findAllById(any())).thenReturn(List.of(article));
      return expectation;
    }
  }
}
