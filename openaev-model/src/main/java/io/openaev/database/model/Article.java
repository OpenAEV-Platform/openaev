package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "articles")
@EntityListeners(ModelBaseListener.class)
public class Article implements Base {

  @Id
  @Column(name = "article_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("article_id")
  @NotBlank
  private String id;

  @Column(name = "article_created_at")
  @JsonProperty("article_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "article_updated_at")
  @JsonProperty("article_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "article_name")
  @JsonProperty("article_name")
  private String name;

  @Column(name = "article_content")
  @JsonProperty("article_content")
  private String content;

  @Column(name = "article_author")
  @JsonProperty("article_author")
  private String author;

  @Column(name = "article_shares")
  @JsonProperty("article_shares")
  private Integer shares;

  @Column(name = "article_likes")
  @JsonProperty("article_likes")
  private Integer likes;

  @Column(name = "article_comments")
  @JsonProperty("article_comments")
  private Integer comments;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("article_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_scenario")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("article_scenario")
  @Schema(type = "string")
  private Scenario scenario;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_channel")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("article_channel")
  @NotNull
  @Schema(type = "string")
  private Channel channel;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "articles_documents",
      joinColumns = @JoinColumn(name = "article_id"),
      inverseJoinColumns = @JoinColumn(name = "document_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("article_documents")
  private List<Document> documents = new ArrayList<>();

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    if (getExercise() != null) {
      return getExercise().isUserHasAccess(user);
    }
    if (getScenario() != null) {
      return getScenario().isUserHasAccess(user);
    }
    return user.isAdmin();
  }

  @Transient private Instant virtualPublication;

  @JsonProperty("article_virtual_publication")
  public Instant getVirtualPublication() {
    return virtualPublication;
  }

  @JsonProperty("article_is_scheduled")
  public boolean isScheduledPublication() {
    return virtualPublication != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
