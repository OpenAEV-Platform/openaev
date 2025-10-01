package io.openaev.rest.channel.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class ArticleUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
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

  @JsonProperty("article_documents")
  private List<String> documents = new ArrayList<>();

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("article_channel")
  private String channelId;

  @JsonProperty("article_published")
  private boolean published;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public Integer getShares() {
    return shares;
  }

  public void setShares(Integer shares) {
    this.shares = shares;
  }

  public Integer getLikes() {
    return likes;
  }

  public void setLikes(Integer likes) {
    this.likes = likes;
  }

  public Integer getComments() {
    return comments;
  }

  public void setComments(Integer comments) {
    this.comments = comments;
  }

  public List<String> getDocuments() {
    return documents;
  }

  public void setDocuments(List<String> documents) {
    this.documents = documents;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }
}
