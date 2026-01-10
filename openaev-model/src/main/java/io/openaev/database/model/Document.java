package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "documents")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Document.tags-scenarios-exercises",
      attributeNodes = {
        @NamedAttributeNode("tags"),
        @NamedAttributeNode("scenarios"),
        @NamedAttributeNode("exercises")
      })
})
public class Document implements Base {

  @Id
  @Column(name = "document_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("document_id")
  @NotBlank
  private String id;

  @Column(name = "document_name")
  @JsonProperty("document_name")
  @Queryable(searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "document_target")
  @JsonProperty("document_target")
  private String target;

  @Column(name = "document_description")
  @JsonProperty("document_description")
  @Queryable(searchable = true, sortable = true)
  private String description;

  @Column(name = "document_type")
  @JsonProperty("document_type")
  @Queryable(searchable = true, sortable = true)
  @NotBlank
  private String type;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "documents_tags",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("document_tags")
  @Queryable(sortable = true)
  private Set<Tag> tags = new HashSet<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "exercises_documents",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "exercise_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("document_exercises")
  private Set<Exercise> exercises = new HashSet<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_documents",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "scenario_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("document_scenarios")
  private Set<Scenario> scenarios = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "articles_documents",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "article_id"))
  @JsonIgnore
  private Set<Article> articles = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "challenges_documents",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "challenge_id"))
  @JsonIgnore
  private Set<Challenge> challenges = new HashSet<>();

  @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<InjectDocument> injectDocuments = new HashSet<>();

  @OneToMany(mappedBy = "fileDropFile", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private Set<FileDrop> payloadsByFileDrop = new HashSet<>();

  @OneToMany(mappedBy = "executableFile", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private Set<Executable> payloadsByExecutableFile = new HashSet<>();

  @OneToMany(mappedBy = "logoDark", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<Channel> channelsByLogoDark = new HashSet<>();

  @OneToMany(mappedBy = "logoLight", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<Channel> channelsByLogoLight = new HashSet<>();

  @OneToMany(mappedBy = "logoDark", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<SecurityPlatform> securityPlatformsByLogoDark = new HashSet<>();

  @OneToMany(mappedBy = "logoLight", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<SecurityPlatform> securityPlatformsByLogoLight = new HashSet<>();

  @OneToMany(mappedBy = "logoDark", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<Exercise> simulationsByLogoDark = new HashSet<>();

  @OneToMany(mappedBy = "logoLight", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<Exercise> simulationsByLogoLight = new HashSet<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.DOCUMENT;

  @Override
  public boolean isUserHasAccess(User user) {
    if (user.isAdmin()) {
      return true;
    }
    return exercises.stream().anyMatch(exercise -> exercise.isUserHasAccess(user))
        || scenarios.stream().anyMatch(scenario -> scenario.isUserHasAccess(user));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
