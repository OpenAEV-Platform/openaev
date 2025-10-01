package io.openaev.rest.channel.output;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Article;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ArticleOutput {

  @JsonProperty("article_id")
  @NotBlank
  private String id;

  @JsonProperty("article_name")
  private String name;

  @JsonProperty("article_content")
  private String content;

  @JsonProperty("article_author")
  private String author;

  @JsonProperty("article_shares")
  private Integer shares;

  @JsonProperty("article_likes")
  private Integer likes;

  @JsonProperty("article_comments")
  private Integer comments;

  @JsonProperty("article_exercise")
  private String exercise;

  @JsonProperty("article_scenario")
  private String scenario;

  @JsonProperty("article_channel")
  @NotBlank
  private String channel;

  @JsonProperty("article_documents")
  private List<String> documents = new ArrayList<>();

  @Transient private Instant virtualPublication;

  @JsonProperty("article_virtual_publication")
  public Instant getVirtualPublication() {
    return this.virtualPublication;
  }

  @JsonProperty("article_is_scheduled")
  public boolean isScheduledPublication() {
    return this.virtualPublication != null;
  }

  public static ArticleOutput from(@org.jetbrains.annotations.NotNull final Article article) {
    ArticleOutput articleOutput = new ArticleOutput();
    articleOutput.setId(article.getId());
    articleOutput.setName(article.getName());
    articleOutput.setContent(article.getContent());
    articleOutput.setAuthor(article.getAuthor());
    articleOutput.setShares(article.getShares());
    articleOutput.setLikes(article.getLikes());
    articleOutput.setComments(article.getComments());
    articleOutput.setExercise(ofNullable(article.getExercise()).map(Exercise::getId).orElse(null));
    articleOutput.setScenario(ofNullable(article.getScenario()).map(Scenario::getId).orElse(null));
    articleOutput.setChannel(article.getChannel().getId());
    articleOutput.setDocuments(article.getDocuments().stream().map(Document::getId).toList());
    articleOutput.setVirtualPublication(article.getVirtualPublication());
    return articleOutput;
  }
}
