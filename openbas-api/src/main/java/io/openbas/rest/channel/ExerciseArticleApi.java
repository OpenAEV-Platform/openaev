package io.openbas.rest.channel;

import static io.openbas.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openbas.rest.channel.ChannelHelper.enrichArticleWithVirtualPublication;
import static io.openbas.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Article;
import io.openbas.database.model.Inject;
import io.openbas.database.model.ResourceType;
import io.openbas.database.repository.ArticleRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.specification.ArticleSpecification;
import io.openbas.database.specification.InjectSpecification;
import io.openbas.rest.channel.output.ArticleOutput;
import io.openbas.rest.helper.RestBehavior;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExerciseArticleApi extends RestBehavior {

  private final InjectRepository injectRepository;
  private final ArticleRepository articleRepository;

  @GetMapping(EXERCISE_URI + "/{exerciseId}/articles")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<ArticleOutput> exerciseArticles(@PathVariable @NotBlank final String exerciseId) {
    List<Inject> injects =
        this.injectRepository.findAll(
            InjectSpecification.fromSimulation(exerciseId)
                .and(InjectSpecification.fromContract(CHANNEL_PUBLISH)));
    List<Article> articles =
        this.articleRepository.findAll(ArticleSpecification.fromExercise(exerciseId));
    return enrichArticleWithVirtualPublication(injects, articles, this.mapper).stream()
        .map(ArticleOutput::from)
        .toList();
  }
}
