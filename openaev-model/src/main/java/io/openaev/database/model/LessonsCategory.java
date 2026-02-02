package io.openaev.database.model;

import static java.time.Instant.now;

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
@Table(name = "lessons_categories")
@EntityListeners(ModelBaseListener.class)
public class LessonsCategory implements Base {

  @Id
  @Column(name = "lessons_category_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonscategory_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_category_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_category_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_category_scenario")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_category_scenario")
  @Schema(type = "string")
  private Scenario scenario;

  @Column(name = "lessons_category_created_at")
  @JsonProperty("lessons_category_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_category_updated_at")
  @JsonProperty("lessons_category_updated_at")
  @NotNull
  private Instant updated = now();

  @Column(name = "lessons_category_name")
  @JsonProperty("lessons_category_name")
  @NotBlank
  private String name;

  @Column(name = "lessons_category_description")
  @JsonProperty("lessons_category_description")
  private String description;

  @Column(name = "lessons_category_order")
  @JsonProperty("lessons_category_order")
  private int order;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "lessons_categories_teams",
      joinColumns = @JoinColumn(name = "lessons_category_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("lessons_category_teams")
  @ArraySchema(schema = @Schema(type = "string"))
  private List<Team> teams = new ArrayList<>();

  @OneToMany(mappedBy = "category", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonProperty("lessons_category_questions")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @ArraySchema(schema = @Schema(type = "string"))
  private List<LessonsQuestion> questions = new ArrayList<>();

  // region transient
  @JsonProperty("lessons_category_users")
  public List<String> getUsers() {
    return getTeams().stream().flatMap(team -> team.getUsers().stream().map(User::getId)).toList();
  }

  // endregion

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
