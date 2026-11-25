package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildMediaPressureVerdict;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.hasNoResults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.channel.model.VirtualArticle;
import io.openaev.rest.channel.response.ChannelReader;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.ExerciseResponseHydrator;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.ScenarioResponseHydrator;
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
  private final ExerciseService exerciseService;
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
   * @param ctx the request's tenant scope, in which the simulation or scenario is looked up
   */
  public ChannelReader validateArticles(
      @NotNull final TxCtx ctx, String exerciseId, String channelId, User user) {
    ChannelReader channelReader;
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);
    List<Inject> injects;

    Optional<Exercise> exerciseOpt = exerciseService.findInScope(ctx, exerciseId);
    if (exerciseOpt.isPresent()) {
      Exercise exercise = exerciseOpt.get();
      channelReader = new ChannelReader(channel, exercise);
      injects = exercise.getInjects();
    } else {
      Scenario scenario = this.scenarioService.scenario(ctx, exerciseId);
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
                      buildMediaPressureVerdict(expectation.getExpectedScore())));
    }
    return withLinksInitialized(channelReader);
  }

  /**
   * Initializes the lazy links the reader serializes inside the caller's tenant-scoped transaction:
   * the associations of the simulation or scenario ({@link ExerciseResponseHydrator}, {@link
   * ScenarioResponseHydrator}), the players of its teams and the {@code article_documents} of every
   * article. The reader holds raw entities, so those collections are otherwise loaded open-in-view
   * after the transaction commits, where the tenant scope is gone: every one of them reads a tenant
   * table and serializes as an empty array with no error.
   *
   * <p>The player list is the distinct users of the {@code teamUsers} rows. Each user is a lazy
   * proxy, and the serializer's distinct reads its hash code, which loads the user and its eager
   * {@code groups}, a tenant table. Initializing every player proxy here runs that load in the
   * scope. The articles cost one statement each, bounded by the articles of one channel in one
   * simulation or scenario.
   */
  public ChannelReader withLinksInitialized(ChannelReader channelReader) {
    if (channelReader.getExercise() != null) {
      ExerciseResponseHydrator.hydrateForResponse(channelReader.getExercise());
      channelReader
          .getExercise()
          .getTeamUsers()
          .forEach(teamUser -> Hibernate.initialize(teamUser.getUser()));
    }
    if (channelReader.getScenario() != null) {
      ScenarioResponseHydrator.hydrateForResponse(channelReader.getScenario());
      channelReader
          .getScenario()
          .getTeamUsers()
          .forEach(teamUser -> Hibernate.initialize(teamUser.getUser()));
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
