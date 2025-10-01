package io.openaev.rest.channel;

import static io.openaev.database.model.Inject.SPEED_STANDARD;
import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Article;
import io.openaev.database.model.Inject;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.channel.model.VirtualArticle;
import io.openaev.rest.exception.ElementNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChannelHelper {

  private ChannelHelper() {}

  public static Article enrichArticleWithVirtualPublication(
      List<Inject> injects, Article article, ObjectMapper mapper) {
    return enrichArticleWithVirtualPublication(injects, List.of(article), mapper).stream()
        .findFirst()
        .orElseThrow(ElementNotFoundException::new);
  }

  public static List<Article> enrichArticleWithVirtualPublication(
      List<Inject> injects, List<Article> articles, ObjectMapper mapper) {
    Instant now = Instant.now();
    Map<String, Instant> toPublishArticleIdsMap =
        injects.stream()
            .filter(
                inject ->
                    inject
                            .getInjectorContract()
                            .map(contract -> contract.getId().equals(CHANNEL_PUBLISH))
                            .orElse(false)
                        && inject.getExercise() != null)
            .filter(inject -> inject.getContent() != null)
            .sorted(Comparator.comparing(Inject::getDependsDuration))
            .flatMap(inject -> convertToVirtualArticles(inject, now, mapper))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toMap(VirtualArticle::id, VirtualArticle::date));
    return articles.stream()
        .peek(article -> article.setVirtualPublication(toPublishArticleIdsMap.get(article.getId())))
        .sorted(
            Comparator.comparing(Article::getVirtualPublication, nullsFirst(naturalOrder()))
                .thenComparing(Article::getCreatedAt)
                .reversed())
        .toList();
  }

  private static Stream<VirtualArticle> convertToVirtualArticles(
      Inject inject, Instant now, ObjectMapper mapper) {
    Instant virtualInjectDate = inject.computeInjectDate(now, SPEED_STANDARD);
    try {
      ChannelContent content = mapper.treeToValue(inject.getContent(), ChannelContent.class);
      return content.getArticles().stream()
          .map(article -> new VirtualArticle(virtualInjectDate, article));
    } catch (JsonProcessingException e) {
      // Log the error if necessary
      return Stream.empty();
    }
  }
}
