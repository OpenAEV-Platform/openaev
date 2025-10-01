package io.openaev.rest.channel;

import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.rest.channel.ChannelHelper.enrichArticleWithVirtualPublication;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Article;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.specification.ArticleSpecification;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.rest.channel.output.ArticleOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioArticleApi extends RestBehavior {

  private final InjectRepository injectRepository;
  private final ArticleRepository articleRepository;

  @GetMapping(SCENARIO_URI + "/{scenarioId}/articles")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<ArticleOutput> scenarioArticles(@PathVariable @NotBlank final String scenarioId) {
    List<Inject> injects =
        this.injectRepository.findAll(
            InjectSpecification.fromScenario(scenarioId)
                .and(InjectSpecification.fromContract(CHANNEL_PUBLISH)));
    List<Article> articles =
        this.articleRepository.findAll(ArticleSpecification.fromScenario(scenarioId));
    return enrichArticleWithVirtualPublication(injects, articles, this.mapper).stream()
        .map(ArticleOutput::from)
        .toList();
  }
}
