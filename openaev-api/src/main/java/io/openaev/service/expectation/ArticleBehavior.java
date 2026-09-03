package io.openaev.service.expectation;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForMediaPressure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.service.InjectExpectationUtils;
import java.util.List;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ArticleInjectExpectation}. */
@Component
public class ArticleBehavior extends AbstractTableTopBehavior {

  private final ArticleRepository articleRepository;
  private final ObjectMapper mapper;

  public ArticleBehavior(
      InjectExpectationRepository injectExpectationRepository,
      ArticleRepository articleRepository,
      ObjectMapper mapper) {
    super(injectExpectationRepository);
    this.articleRepository = articleRepository;
    this.mapper = mapper;
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ArticleInjectExpectation;
  }

  @Override
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return type == BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE;
  }

  @Override
  protected InjectExpectationResult buildDefaultPlayerResult(Double expectedScore) {
    return buildDefaultForMediaPressure();
  }

  @Override
  public ArticleInjectExpectation convertFormExpectationToBaseInjectExpectation(
      io.openaev.model.inject.form.Expectation formExpectation, Exercise exercise, Inject inject) {
    ArticleInjectExpectation articleExpectation = new ArticleInjectExpectation();
    InjectExpectationUtils.setCommonFields(
        articleExpectation, formExpectation, exercise, inject, this.expectationPropertiesConfig);
    return articleExpectation;
  }

  /**
   * Expands the article template into one template per article referenced by the inject content, so
   * each article gets its own expectation tree.
   */
  @Override
  protected List<TableTopInjectExpectation> expandTemplatesForContext(
      ExecutableInject executableInject, TableTopInjectExpectation template) {
    return resolveArticles(executableInject).stream()
        .map(
            article -> {
              ArticleInjectExpectation expectation = (ArticleInjectExpectation) template.clone();
              expectation.setArticle(article);
              return (TableTopInjectExpectation) expectation;
            })
        .toList();
  }

  private List<Article> resolveArticles(ExecutableInject executableInject) {
    List<Article> cached = executableInject.getExpectationContext(Article.class);
    if (!cached.isEmpty()) {
      return cached;
    }
    try {
      ChannelContent content =
          mapper.treeToValue(
              executableInject.getInjection().getInject().getContent(), ChannelContent.class);
      return fromIterable(articleRepository.findAllById(content.getArticles()));
    } catch (Exception e) {
      return List.of();
    }
  }
}
