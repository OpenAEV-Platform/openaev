package io.openbas.service;

import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openbas.utils.inject_expectation_result.InjectExpectationResultUtils.buildForMediaPressure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.*;
import io.openbas.database.repository.ArticleRepository;
import io.openbas.database.repository.ChannelRepository;
import io.openbas.database.repository.ExerciseRepository;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.injectors.channel.model.ChannelContent;
import io.openbas.rest.channel.model.VirtualArticle;
import io.openbas.rest.channel.response.ChannelReader;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.utils.ExpectationUtils;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChannelService {

  private final InjectExpectationRepository injectExpectationExecutionRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioService scenarioService;
  private final ArticleRepository articleRepository;
  private final ChannelRepository channelRepository;
  @Resource protected ObjectMapper mapper;

  public Channel channel(@NotNull final String channelId) {
    return channelRepository
        .findById(channelId)
        .orElseThrow(() -> new ElementNotFoundException("Channel not found with id: " + channelId));
  }

  public ChannelReader validateArticles(String exerciseId, String channelId, User user) {
    ChannelReader channelReader;
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);
    List<Inject> injects;

    Optional<Exercise> exerciseOpt = exerciseRepository.findById(exerciseId);
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
      // Fulfill article expectations
      List<Inject> finalInjects = injects;
      List<InjectExpectation> expectationExecutions =
          publishedArticles.stream()
              .flatMap(
                  article ->
                      finalInjects.stream()
                          .flatMap(
                              inject ->
                                  inject.getUserExpectationsForArticle(user, article).stream()))
              .filter(exec -> exec.getResults().isEmpty())
              .toList();

      // Update all expectations linked to player
      expectationExecutions.forEach(
          injectExpectationExecution -> {
            injectExpectationExecution.setResults(
                List.of(buildForMediaPressure(injectExpectationExecution)));
            injectExpectationExecution.setScore(injectExpectationExecution.getExpectedScore());
            injectExpectationExecution.setUpdatedAt(Instant.now());
            injectExpectationExecutionRepository.save(injectExpectationExecution);
          });

      // -- VALIDATION TYPE --
      processByValidationType(user, injects, publishedArticles, expectationExecutions.size() > 0);
    }
    return channelReader;
  }

  private void processByValidationType(
      User user,
      List<Inject> injects,
      List<Article> publishedArticles,
      boolean isaNewExpectationResult) {
    // Process expectation linked to teams where user if part of
    List<String> injectIds = injects.stream().map(Inject::getId).toList();
    List<String> teamIds = user.getTeams().stream().map(Team::getId).toList();
    List<String> articleIds =
        publishedArticles.stream().map(Article::getId).toList(); // Articles with the same channel
    // Find all expectations linked to teams' user, channel and exercise
    List<InjectExpectation> channelExpectations =
        injectExpectationExecutionRepository.findChannelExpectations(
            injectIds, teamIds, articleIds);
    List<InjectExpectation> parentExpectations =
        channelExpectations.stream().filter(exp -> exp.getUser() == null).toList();
    Map<Team, List<InjectExpectation>> playerByTeam =
        channelExpectations.stream()
            .filter(exp -> exp.getUser() != null)
            .collect(Collectors.groupingBy(InjectExpectation::getTeam));

    // Depending on type of validation, we process the parent expectations:
    List<InjectExpectation> toUpdate =
        ExpectationUtils.processByValidationType(
            isaNewExpectationResult, channelExpectations, parentExpectations, playerByTeam);
    injectExpectationExecutionRepository.saveAll(toUpdate);
  }

  public List<Channel> channelsForSimulation(@NotBlank final String simulationId) {
    return fromIterable(this.channelRepository.findDistinctByArticlesExerciseId(simulationId));
  }

  public List<Channel> channelsForScenario(@NotBlank final String scenarioId) {
    return fromIterable(this.channelRepository.findDistinctByArticlesScenarioId(scenarioId));
  }
}
