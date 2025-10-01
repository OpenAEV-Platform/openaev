package io.openaev.utils.fixtures;

import io.openaev.database.model.Article;
import io.openaev.database.model.Channel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class ArticleFixture {

  public static final String ARTICLE_NAME = "An article";

  public static Article getDefaultArticle() {
    Article article = createDefaultArticleWithDefaultName();
    article.setContent("Lorem");
    article.setShares(1);
    article.setLikes(2);
    article.setComments(4);
    article.setAuthor("Charles Foster Kane");
    return article;
  }

  public static Article getArticle(@NotNull final Channel channel) {
    Article article = createDefaultArticleWithName(ARTICLE_NAME);
    article.setContent("Lorem");
    article.setChannel(channel);
    return article;
  }

  public static Article getArticleNoChannel() {
    Article article = createDefaultArticleWithName(ARTICLE_NAME);
    article.setContent("Lorem");
    article.setShares(1);
    article.setLikes(2);
    article.setComments(4);
    article.setAuthor("Charles Foster Kane");
    return article;
  }

  private static Article createDefaultArticleWithDefaultName() {
    return createDefaultArticleWithName(null);
  }

  private static Article createDefaultArticleWithName(String name) {
    String new_name = name == null ? "article-%s".formatted(UUID.randomUUID()) : name;
    Article article = new Article();
    article.setName(new_name);
    return article;
  }
}
