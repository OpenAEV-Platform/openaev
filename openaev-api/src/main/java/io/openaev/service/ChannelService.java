package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildMediaPressureUpdateInput;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.hasNoResults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.channel.model.VirtualArticle;
import io.openaev.rest.channel.response.ChannelReader;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChannelService {

  private final InjectExpectationService injectExpectationService;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioService scenarioService;
  private final ArticleRepository articleRepository;
  private final ChannelRepository channelRepository;
  private final ObjectMapper mapper;

  public Channel channel(@NotNull final String channelId) {
    return channelRepository
        .findById(channelId)
        .orElseThrow(() -> new ElementNotFoundException("Channel not found with id: " + channelId));
  }

  public boolean existsById(@NotBlank final String channelId) {
    return channelRepository.existsById(channelId);
  }

  // -- DELETE --

  // existsById() goes through tenant-scoped repository SQL for channels, so deleteById() below is
  // safe for the caller's current scope.
  public void deleteChannel(@NotBlank final String channelId) {
    if (!channelRepository.existsById(channelId)) {
      throw new ElementNotFoundException("Channel not found with id: " + channelId);
    }
    channelRepository.deleteById(channelId);
  }

  /**
   * @param tenantId resolved by the caller (API layer), not read from TenantContext here.
   */
  public ChannelReader validateArticles(
      String exerciseId, String channelId, User user, @NotBlank final String tenantId) {
    ChannelReader channelReader;
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);
    List<Inject> injects;

    Optional<Exercise> exerciseOpt = exerciseRepository.findByIdAndTenantId(exerciseId, tenantId);
    if (exerciseOpt.isPresent()) {
      Exercise exercise = exerciseOpt.get();
      channelReader = new ChannelReader(channel, exercise);
      injects = exercise.getInjects();
    } else {
      Scenario scenario = this.scenarioService.scenario(exerciseId);
      channelReader = new ChannelReader(channel, scenario);
      injects = scenario.getInjects();
    }

    Map<String, Instant> toPublishArticleIdsMap =
        injects.stream()
            .filter(
                inject ->
                    inject
                        .getInjectorContract()
                        .map(contract -> contract.getId().equals(CHANNEL_PUBLISH))
                        .orElse(false))
            .filter(inject -> inject.getStatus().isPresent())
            .sorted(Comparator.comparing(inject -> inject.getStatus().get().getTrackingSentDate()))
            .flatMap(
                inject -> {
                  Instant virtualInjectDate = inject.getStatus().get().getTrackingSentDate();
                  try {
                    ChannelContent content =
                        mapper.treeToValue(inject.getContent(), ChannelContent.class);
                    if (content.getArticles() != null) {
                      return content.getArticles().stream()
                          .map(article -> new VirtualArticle(virtualInjectDate, article));
                    }
                    return null;
                  } catch (JsonProcessingException e) {
                    // Invalid channel content.
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toMap(VirtualArticle::id, VirtualArticle::date));
    if (!toPublishArticleIdsMap.isEmpty()) {
      List<Article> publishedArticles =
          fromIterable(articleRepository.findAllById(toPublishArticleIdsMap.keySet())).stream()
              .filter(article -> article.getChannel().equals(channel))
              .peek(
                  article ->
                      article.setVirtualPublication(toPublishArticleIdsMap.get(article.getId())))
              .sorted(Comparator.comparing(Article::getVirtualPublication).reversed())
              .toList();
      channelReader.setChannelArticles(publishedArticles);
      // Fulfill the player's article expectations not read yet; the behavior recomputes the teams
      publishedArticles.stream()
          .flatMap(
              article ->
                  injects.stream()
                      .flatMap(
                          inject -> inject.getUserExpectationsForArticle(user, article).stream()))
          .filter(expectation -> hasNoResults(expectation.getResults()))
          .forEach(
              expectation ->
                  injectExpectationService.updateInjectExpectation(
                      expectation.getId(),
                      buildMediaPressureUpdateInput(expectation.getExpectedScore())));
    }
    return withDocumentLinksInitialized(channelReader);
  }

  /**
   * Initializes the lazy document links the reader serializes ({@code article_documents} on every
   * article, {@code exercise_documents} or {@code scenario_documents} on the parent) inside the
   * caller's tenant-scoped transaction. The reader holds raw entities, so those collections are
   * otherwise loaded open-in-view after the transaction commits, where the tenant scope is gone:
   * with {@code documents} tenant-active the loads fail closed to empty arrays and the channel
   * pages lose their media with no error. One statement per article, bounded by the articles of one
   * channel in one simulation or scenario.
   */
  public ChannelReader withDocumentLinksInitialized(ChannelReader channelReader) {
    if (channelReader.getExercise() != null) {
      Hibernate.initialize(channelReader.getExercise().getDocuments());
    }
    if (channelReader.getScenario() != null) {
      Hibernate.initialize(channelReader.getScenario().getDocuments());
    }
    channelReader
        .getChannelArticles()
        .forEach(article -> Hibernate.initialize(article.getDocuments()));
    return channelReader;
  }

  public List<Channel> channelsForSimulation(@NotBlank final String simulationId) {
    return fromIterable(this.channelRepository.findDistinctByArticlesExerciseId(simulationId));
  }

  public List<Channel> channelsForScenario(@NotBlank final String scenarioId) {
    return fromIterable(this.channelRepository.findDistinctByArticlesScenarioId(scenarioId));
  }
}
