package io.openaev.injectors.channel;

import static io.openaev.database.model.ExecutionTrace.*;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injector_contract.variables.contract.UserContract;
import io.openaev.injectors.channel.model.ArticleVariable;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelExecutor extends Injector {

  public static final String VARIABLE_ARTICLES = "articles";
  public static final String VARIABLE_ARTICLE = "article";

  private final ArticleRepository articleRepository;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;
  private final UrlAccessTokenService urlAccessTokenService;

  public ChannelExecutor(
      InjectorContext context,
      ArticleRepository articleRepository,
      EmailService emailService,
      InjectExpectationService injectExpectationService,
      UrlAccessTokenService urlAccessTokenService) {
    super(context);
    this.articleRepository = articleRepository;
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
    this.urlAccessTokenService = urlAccessTokenService;
  }

  private String buildArticleUri(
      ExecutionContext executionContext, Article article, Exercise exercise, String tenantId) {
    UserContract user = executionContext.getUser();
    String channelId = article.getChannel().getId();
    String queryOptions = "article=" + article.getId();
    String url =
        this.context.getOpenAEVConfig().getBaseUrl()
            + "/"
            + tenantId
            + "/channels/"
            + exercise.getId()
            + "/"
            + channelId
            + "?"
            + queryOptions;
    return urlAccessTokenService.generateTokenUrl(exercise, user, url);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection) {
    try {
      ChannelContent content =
          injectExpectationService.contentConvert(injection, ChannelContent.class);
      List<Article> articles = fromIterable(articleRepository.findAllById(content.getArticles()));
      if (articles.isEmpty()) {
        throw new UnsupportedOperationException("Inject needs at least one article");
      }
      injection.cacheExpectationContext(articles);
      String contract =
          injection
              .getInjection()
              .getInject()
              .getInjectorContract()
              .map(InjectorContract::getId)
              .orElseThrow(
                  () -> new UnsupportedOperationException("Inject does not have a contract"));

      if (contract.equals(CHANNEL_PUBLISH)) {
        // Article publishing is only linked to execution date of this inject.
        String articleNames =
            articles.stream().map(Article::getName).collect(Collectors.joining(","));
        String publishedMessage = "Articles (" + articleNames + ") marked as published";
        execution.addTrace(getNewSuccessTrace(publishedMessage, ExecutionTraceAction.COMPLETE));

        Exercise exercise = injection.getInjection().getExercise();
        // Send the publication message.
        if (content.isEmailing()) {
          String from = exercise.getFrom();
          String fromName = exercise.getFromName();
          List<String> replyTos = exercise.getReplyTos();
          List<ExecutionContext> users = injection.getUsers();
          List<Document> documents =
              injection.getInjection().getInject().getDocuments().stream()
                  .filter(InjectDocument::isAttached)
                  .map(InjectDocument::getDocument)
                  .toList();
          List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
          String message =
              content.buildMessage(injection, this.context.getOpenAEVConfig().getBaseUrl());
          boolean encrypted = content.isEncrypted();
          String tenantId = exercise.getTenant().getId();
          users.forEach(
              userInjectContext -> {
                try {
                  // Put the articles variables in the injection context
                  List<ArticleVariable> articleVariables =
                      articles.stream()
                          .map(
                              article ->
                                  new ArticleVariable(
                                      article.getId(),
                                      article.getName(),
                                      buildArticleUri(
                                          userInjectContext, article, exercise, tenantId)))
                          .toList();
                  userInjectContext.put(VARIABLE_ARTICLES, articleVariables);
                  // Send the email.
                  emailService.sendEmail(
                      execution,
                      List.of(userInjectContext),
                      from,
                      fromName,
                      replyTos,
                      content.getInReplyTo(),
                      encrypted,
                      content.getSubject(),
                      message,
                      attachments);
                } catch (Exception e) {
                  execution.addTrace(
                      getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
                }
              });
        } else {
          execution.addTrace(
              getNewInfoTrace("Email disabled for this inject", ExecutionTraceAction.EXECUTION));
        }

        injectExpectationService.computeAndSaveExpectations(
            injection, content.getExpectations(), null);

        return new ExecutionProcess(false);
      } else {
        throw new UnsupportedOperationException("Unknown contract " + contract);
      }
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    }
    return new ExecutionProcess(false);
  }
}
