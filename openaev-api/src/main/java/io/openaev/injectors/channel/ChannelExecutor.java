package io.openaev.injectors.channel;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;
import static io.openaev.database.model.ExecutionTrace.getNewSuccessTrace;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;

import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.channel.model.ArticleVariable;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.ChannelExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;

public class ChannelExecutor extends Injector {

  public static final String VARIABLE_ARTICLES = "articles";
  public static final String VARIABLE_ARTICLE = "article";

  private final ArticleRepository articleRepository;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  public ChannelExecutor(
      InjectorContext context,
      ArticleRepository articleRepository,
      EmailService emailService,
      InjectExpectationService injectExpectationService) {
    super(context);
    this.articleRepository = articleRepository;
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
  }

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  private String buildArticleUri(ExecutionContext executionContext, Article article) {
    String userId = executionContext.getUser().getId();
    String channelId = article.getChannel().getId();
    String exerciseId = article.getExercise().getId();
    String queryOptions = "article=" + article.getId() + "&user=" + userId;
    return this.context.getOpenAEVConfig().getBaseUrl()
        + "/channels/"
        + exerciseId
        + "/"
        + channelId
        + "?"
        + queryOptions;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection) {
    try {
      ChannelContent content = contentConvert(injection, ChannelContent.class);
      List<Article> articles = fromIterable(articleRepository.findAllById(content.getArticles()));
      if (articles.isEmpty()) {
        throw new UnsupportedOperationException("Inject needs at least one article");
      }
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
          List<String> replyTos = exercise.getReplyTos();
          List<ExecutionContext> users = injection.getUsers();
          List<Document> documents =
              injection.getInjection().getInject().getDocuments().stream()
                  .filter(InjectDocument::isAttached)
                  .map(InjectDocument::getDocument)
                  .toList();
          List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
          String message = content.buildMessage(injection, imapEnabled);
          boolean encrypted = content.isEncrypted();
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
                                      buildArticleUri(userInjectContext, article)))
                          .toList();
                  userInjectContext.put(VARIABLE_ARTICLES, articleVariables);
                  // Send the email.
                  emailService.sendEmail(
                      execution,
                      List.of(userInjectContext),
                      from,
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
        List<Expectation> expectations = new ArrayList<>();
        if (!content.getExpectations().isEmpty()) {
          expectations.addAll(
              content.getExpectations().stream()
                  .flatMap(
                      (entry) ->
                          switch (entry.getType()) {
                            case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                            case ARTICLE ->
                                articles.stream()
                                    .map(
                                        article ->
                                            (Expectation) new ChannelExpectation(entry, article));
                            default -> Stream.of();
                          })
                  .toList());
        }

        injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

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
