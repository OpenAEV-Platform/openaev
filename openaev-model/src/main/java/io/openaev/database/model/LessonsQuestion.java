package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.openaev.helper.MultiIdListDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "lessons_questions")
@EntityListeners(ModelBaseListener.class)
public class LessonsQuestion implements Base {

  @Id
  @Column(name = "lessons_question_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonsquestion_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_question_category")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("lessons_question_category")
  @NotNull
  @Schema(type = "string")
  private LessonsCategory category;

  @Column(name = "lessons_question_created_at")
  @JsonProperty("lessons_question_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_question_updated_at")
  @JsonProperty("lessons_question_updated_at")
  @NotNull
  private Instant updated = now();

  @Column(name = "lessons_question_content")
  @JsonProperty("lessons_question_content")
  @NotBlank
  private String content;

  @Column(name = "lessons_question_explanation")
  @JsonProperty("lessons_question_explanation")
  private String explanation;

  @Column(name = "lessons_question_order")
  @JsonProperty("lessons_question_order")
  private int order;

  @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonProperty("lessons_question_answers")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string"))
  private List<LessonsAnswer> answers = new ArrayList<>();

  // region transient
  @JsonProperty("lessons_question_exercise")
  public String getExercise() {
    return Optional.ofNullable(getCategory().getExercise()).map(Exercise::getId).orElse(null);
  }

  @JsonProperty("lessons_question_scenario")
  public String getScenario() {
    return Optional.ofNullable(getCategory().getScenario()).map(Scenario::getId).orElse(null);
  }

  // endregion

  @Override
  public boolean isUserHasAccess(User user) {
    return getCategory().getExercise().isUserHasAccess(user);
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
