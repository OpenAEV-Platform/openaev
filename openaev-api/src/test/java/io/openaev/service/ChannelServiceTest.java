package io.openaev.service;

import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.ArticleFixture;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.time.Instant;
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

  @Mock private InjectExpectationRepository injectExpectationExecutionRepository;
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
    @DisplayName("Given article expectation should validate")
    void given_expectation_should_validate() {
      // Arrange
      User user = UserFixture.getUser();
      user.setId("user-1");

      Channel channel = ChannelFixture.getDefaultChannel();
      channel.setId("channel-1");

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

      Double expectedScore = 100.0;
      InjectExpectation expectation = new InjectExpectation();
      expectation.setId("expectation-1");
      expectation.setType(InjectExpectation.EXPECTATION_TYPE.ARTICLE);
      expectation.setExpectedScore(expectedScore);
      expectation.setArticle(article);
      expectation.setUser(user);
      expectation.setResults(null); // null results — previously caused NPE with isEmpty()

      inject.setExpectations(List.of(expectation));

      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setId("exercise-1");
      exercise.setInjects(List.of(inject));

      when(channelRepository.findById("channel-1")).thenReturn(Optional.of(channel));
      when(exerciseRepository.findById("exercise-1")).thenReturn(Optional.of(exercise));
      when(articleRepository.findAllById(any())).thenReturn(List.of(article));
      when(injectExpectationExecutionRepository.findChannelExpectations(any(), any(), any()))
          .thenReturn(List.of());

      // Act
      assertDoesNotThrow(() -> channelService.validateArticles("exercise-1", "channel-1", user));

      // Assert
      ArgumentCaptor<InjectExpectation> captor = ArgumentCaptor.forClass(InjectExpectation.class);
      verify(injectExpectationExecutionRepository).save(captor.capture());
      InjectExpectation saved = captor.getValue();
      assertEquals(expectedScore, saved.getScore());
      assertNotNull(saved.getResults());
      assertFalse(saved.getResults().isEmpty());
    }
  }
}
