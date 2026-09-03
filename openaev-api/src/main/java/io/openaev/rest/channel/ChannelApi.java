package io.openaev.rest.channel;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.channel.ChannelHelper.enrichArticleWithVirtualPublication;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UrlAccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.*;
import io.openaev.rest.channel.form.*;
import io.openaev.rest.channel.response.ChannelReader;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.ChannelService;
import io.openaev.service.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChannelApi extends RestBehavior {

  public static final String CHANNEL_URI = "/api/channels";
  private static final String TENANT_CHANNEL_URI = TENANT_PREFIX + "/channels";
  private static final String OBSERVER_CHANNEL_URI = "/api/observer/channels";
  private static final String TENANT_OBSERVER_CHANNEL_URI = TENANT_PREFIX + "/observer/channels";
  private static final String PLAYER_CHANNEL_URI = "/api/player/channels";
  private static final String TENANT_PLAYER_CHANNEL_URI = TENANT_PREFIX + "/player/channels";

  private final ExerciseRepository exerciseRepository;
  private final ScenarioService scenarioService;
  private final ArticleRepository articleRepository;
  private final ChannelRepository channelRepository;
  private final DocumentRepository documentRepository;
  private final UserRepository userRepository;
  private final ChannelService channelService;
  private final DocumentService documentService;

  // -- CHANNELS --

  @GetMapping({CHANNEL_URI, TENANT_CHANNEL_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.CHANNEL)
  public Iterable<Channel> channels(TxCtx ctx) {
    return channelRepository.findAll();
  }

  @GetMapping({CHANNEL_URI + "/{channelId}", TENANT_CHANNEL_URI + "/{channelId}"})
  @Transactional
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CHANNEL)
  public Channel channel(@PathVariable String channelId) {
    return channelService.channel(channelId);
  }

  @PutMapping({CHANNEL_URI + "/{channelId}", TENANT_CHANNEL_URI + "/{channelId}"})
  @Transactional
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CHANNEL)
  public Channel updateChannel(
      @PathVariable String channelId, @Valid @RequestBody ChannelUpdateInput input) {
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);
    channel.setUpdateAttributes(input);
    channel.setUpdatedAt(Instant.now());
    return channelRepository.save(channel);
  }

  @PutMapping({CHANNEL_URI + "/{channelId}/logos", TENANT_CHANNEL_URI + "/{channelId}/logos"})
  @Transactional
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CHANNEL)
  public Channel updateChannelLogos(
      @PathVariable String channelId, @Valid @RequestBody ChannelUpdateLogoInput input) {
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);
    if (input.getLogoDark() != null) {
      channel.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    } else {
      channel.setLogoDark(null);
    }
    if (input.getLogoLight() != null) {
      channel.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    } else {
      channel.setLogoLight(null);
    }
    return channelRepository.save(channel);
  }

  @PostMapping({CHANNEL_URI, TENANT_CHANNEL_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.CHANNEL)
  @Transactional(rollbackFor = Exception.class)
  public Channel createChannel(TxCtx ctx, @Valid @RequestBody ChannelCreateInput input) {
    Channel channel = new Channel();
    channel.setUpdateAttributes(input);
    return channelRepository.save(channel);
  }

  @DeleteMapping({CHANNEL_URI + "/{channelId}", TENANT_CHANNEL_URI + "/{channelId}"})
  @Transactional
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.CHANNEL)
  public void deleteChannel(@PathVariable String channelId) {
    channelService.deleteChannel(channelId);
  }

  @GetMapping({
    OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}",
    TENANT_OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public ChannelReader observerArticles(
      @PathVariable String exerciseId, @PathVariable String channelId) {
    ChannelReader channelReader;
    Channel channel =
        channelRepository.findById(channelId).orElseThrow(ElementNotFoundException::new);

    Optional<Exercise> exerciseOpt =
        this.exerciseRepository.findByIdAndTenantId(exerciseId, TenantContext.getCurrentTenant());
    if (exerciseOpt.isPresent()) {
      Exercise exercise = exerciseOpt.get();
      channelReader = new ChannelReader(channel, exercise);
      List<Article> publishedArticles = exercise.getArticlesForChannel(channel);
      List<Article> articles =
          enrichArticleWithVirtualPublication(
              exercise.getInjects(), publishedArticles, this.mapper);
      channelReader.setChannelArticles(articles);
    } else {
      Scenario scenario = this.scenarioService.scenario(exerciseId);
      channelReader = new ChannelReader(channel, scenario);
      List<Article> publishedArticles = scenario.getArticlesForChannel(channel);
      List<Article> articles =
          enrichArticleWithVirtualPublication(
              scenario.getInjects(), publishedArticles, this.mapper);
      channelReader.setChannelArticles(articles);
    }
    return channelReader;
  }

  @GetMapping({
    PLAYER_CHANNEL_URI + "/{exerciseId}/{channelId}",
    TENANT_PLAYER_CHANNEL_URI + "/{exerciseId}/{channelId}"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public ChannelReader playerArticles(
      @PathVariable String exerciseId,
      @PathVariable String channelId,
      @RequestParam Optional<String> userId)
      throws AuthenticationError {
    final User user = impersonateUser(userRepository, userId);
    // TenantContext resolved here (API layer) and passed explicitly to the service, per review
    // feedback on keeping tenant resolution out of the Service layer.
    return channelService.validateArticles(
        exerciseId, channelId, user, TenantContext.getCurrentTenant());
  }

  // -- EXERCISES --

  @PostMapping({
    "/api/exercises/{exerciseId}/articles",
    TENANT_EXERCISE_URI + "/{exerciseId}/articles"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Article createArticleForExercise(
      @PathVariable String exerciseId, @Valid @RequestBody ArticleCreateInput input) {
    Exercise exercise =
        exerciseRepository
            .findByIdAndTenantId(exerciseId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Article article = new Article();
    article.setUpdateAttributes(input);
    article.setChannel(
        channelRepository
            .findById(input.getChannelId())
            .orElseThrow(ElementNotFoundException::new));
    article.setExercise(exercise);
    Article savedArticle = articleRepository.save(article);
    List<String> articleDocuments = input.getDocuments();
    List<Document> finalArticleDocuments = new ArrayList<>();
    articleDocuments.forEach(
        articleDocument -> {
          Optional<Document> doc = documentRepository.findById(articleDocument);
          if (doc.isPresent()) {
            Document document = doc.get();
            finalArticleDocuments.add(document);
            // If Document not yet linked directly to the exercise, attached it
            if (!document.getExercises().contains(exercise)) {
              exercise.getDocuments().add(document);
              exerciseRepository.save(exercise);
            }
          }
        });
    savedArticle.setDocuments(finalArticleDocuments);
    return enrichArticleWithVirtualPublication(exercise.getInjects(), savedArticle, this.mapper);
  }

  @PutMapping({
    "/api/exercises/{exerciseId}/articles/{articleId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/articles/{articleId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Article updateArticleForExercise(
      @PathVariable String exerciseId,
      @PathVariable String articleId,
      @Valid @RequestBody ArticleUpdateInput input) {
    Exercise exercise =
        exerciseRepository
            .findByIdAndTenantId(exerciseId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Article article =
        articleRepository.findById(articleId).orElseThrow(ElementNotFoundException::new);
    List<String> newDocumentsIds = input.getDocuments();
    List<String> currentDocumentIds = article.getDocuments().stream().map(Document::getId).toList();
    article.setChannel(
        channelRepository
            .findById(input.getChannelId())
            .orElseThrow(ElementNotFoundException::new));
    article.setUpdateAttributes(input);
    // Original List
    List<Document> articleDocuments = new ArrayList<>(article.getDocuments());
    // region Set documents
    // To delete
    article.getDocuments().stream()
        .filter(articleDoc -> !newDocumentsIds.contains(articleDoc.getId()))
        .forEach(articleDocuments::remove);
    // To add
    newDocumentsIds.stream()
        .filter(doc -> !currentDocumentIds.contains(doc))
        .forEach(
            in -> {
              Optional<Document> doc = documentRepository.findById(in);
              if (doc.isPresent()) {
                Document document = doc.get();
                articleDocuments.add(document);
                // If Document not yet linked directly to the exercise, attached it
                if (!document.getExercises().contains(exercise)) {
                  exercise.getDocuments().add(document);
                  exerciseRepository.save(exercise);
                }
              }
            });
    article.setDocuments(articleDocuments);
    Article savedArticle = articleRepository.save(article);
    return enrichArticleWithVirtualPublication(exercise.getInjects(), savedArticle, this.mapper);
  }

  @DeleteMapping({
    "/api/exercises/{exerciseId}/articles/{articleId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/articles/{articleId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteArticleForExercise(
      @PathVariable String exerciseId, @PathVariable String articleId) {
    articleRepository.deleteById(articleId);
  }

  // -- SCENARIOS --

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/articles",
    TENANT_SCENARIO_URI + "/{scenarioId}/articles"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Article createArticleForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody ArticleCreateInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Article article = new Article();
    article.setUpdateAttributes(input);
    article.setChannel(
        this.channelRepository
            .findById(input.getChannelId())
            .orElseThrow(ElementNotFoundException::new));
    article.setScenario(scenario);
    Article savedArticle = this.articleRepository.save(article);
    List<String> articleDocuments = input.getDocuments();
    List<Document> finalArticleDocuments = new ArrayList<>();
    articleDocuments.forEach(
        articleDocument -> {
          Optional<Document> doc = this.documentRepository.findById(articleDocument);
          if (doc.isPresent()) {
            Document document = doc.get();
            finalArticleDocuments.add(document);
            // If Document not yet linked directly to the exercise, attached it
            if (!document.getScenarios().contains(scenario)) {
              scenario.getDocuments().add(document);
              this.scenarioService.updateScenario(scenario);
            }
          }
        });
    savedArticle.setDocuments(finalArticleDocuments);
    return enrichArticleWithVirtualPublication(scenario.getInjects(), savedArticle, this.mapper);
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/articles/{articleId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/articles/{articleId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Article updateArticleForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String articleId,
      @Valid @RequestBody ArticleUpdateInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Article article =
        articleRepository.findById(articleId).orElseThrow(ElementNotFoundException::new);
    List<String> newDocumentsIds = input.getDocuments();
    List<String> currentDocumentIds = article.getDocuments().stream().map(Document::getId).toList();
    article.setChannel(
        channelRepository
            .findById(input.getChannelId())
            .orElseThrow(ElementNotFoundException::new));
    article.setUpdateAttributes(input);
    // Original List
    List<Document> articleDocuments = new ArrayList<>(article.getDocuments());
    // region Set documents
    // To delete
    article.getDocuments().stream()
        .filter(articleDoc -> !newDocumentsIds.contains(articleDoc.getId()))
        .forEach(articleDocuments::remove);
    // To add
    newDocumentsIds.stream()
        .filter(doc -> !currentDocumentIds.contains(doc))
        .forEach(
            in -> {
              Optional<Document> doc = documentRepository.findById(in);
              if (doc.isPresent()) {
                Document document = doc.get();
                articleDocuments.add(document);
                // If Document not yet linked directly to the exercise, attached it
                if (!document.getScenarios().contains(scenario)) {
                  scenario.getDocuments().add(document);
                  this.scenarioService.updateScenario(scenario);
                }
              }
            });
    article.setDocuments(articleDocuments);
    Article savedArticle = articleRepository.save(article);
    return enrichArticleWithVirtualPublication(scenario.getInjects(), savedArticle, this.mapper);
  }

  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/articles/{articleId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/articles/{articleId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public void deleteArticleForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String articleId) {
    articleRepository.deleteById(articleId);
  }

  @GetMapping({
    CHANNEL_URI + "/{channelId}/documents",
    TENANT_CHANNEL_URI + "/{channelId}/documents"
  })
  @AccessControl(
      resourceId = "#channelId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CHANNEL)
  @Operation(summary = "Get the Documents used in a channel")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Documents used in the Channel")
      })
  public List<RawDocument> documentsFromChannel(@PathVariable String channelId) {
    return documentService.documentsForChannel(channelId);
  }
}
