package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.openaev.helper.MultiIdSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Entity
@Table(name = "logs")
@EntityListeners(ModelBaseListener.class)
public class Log implements Base {

  @Id
  @Column(name = "log_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("log_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "log_exercise")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("log_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "log_user")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("log_user")
  @Schema(type = "string")
  private User user;

  @Column(name = "log_title")
  @JsonProperty("log_title")
  @NotBlank
  private String title;

  @Column(name = "log_content")
  @JsonProperty("log_content")
  @NotBlank
  private String content;

  @Column(name = "log_created_at")
  @JsonProperty("log_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "log_updated_at")
  @JsonProperty("log_updated_at")
  @NotNull
  private Instant updated = now();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "logs_tags",
      joinColumns = @JoinColumn(name = "log_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("log_tags")
  private Set<Tag> tags = new HashSet<>();

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return exercise.isUserHasAccess(user);
  }

  public Exercise getExercise() {
    return exercise;
  }

  public User getUser() {
    return user;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public Set<Tag> getTags() {
    return tags;
  }

  public Instant getCreated() {
    return created;
  }

  public Instant getUpdated() {
    return updated;
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
