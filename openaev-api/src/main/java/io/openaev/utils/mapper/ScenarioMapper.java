package io.openaev.utils.mapper;

import io.openaev.database.model.Article;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ScenarioMapper {

  public ScenarioSimple toScenarioSimple(@NotNull final Scenario scenario) {
    ScenarioSimple simple = new ScenarioSimple();
    BeanUtils.copyProperties(scenario, simple);
    return simple;
  }

  public static Set<RelatedEntityOutput> toScenarioArticles(Set<Article> articles) {
    return articles.stream().map(article -> toScenarioArticle(article)).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toScenarioArticle(Article article) {
    return RelatedEntityOutput.builder()
        .id(article.getId())
        .name(article.getName())
        .context(article.getScenario().getId())
        .build();
  }

  public static Set<RelatedEntityOutput> toScenarioInjects(Set<Inject> injects) {
    return injects.stream().map(inject -> toScenarioInject(inject)).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toScenarioInject(Inject inject) {
    return RelatedEntityOutput.builder()
        .id(inject.getId())
        .name(inject.getTitle())
        .context(inject.getScenario().getId())
        .build();
  }
}
