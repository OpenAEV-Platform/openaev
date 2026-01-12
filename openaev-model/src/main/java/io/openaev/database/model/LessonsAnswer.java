package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "lessons_answers")
@EntityListeners(ModelBaseListener.class)
public class LessonsAnswer implements Base {

  @Id
  @Column(name = "lessons_answer_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonsanswer_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_answer_question")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_answer_question")
  @NotNull
  @Schema(type = "string")
  private LessonsQuestion question;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_answer_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_answer_user")
  @Schema(type = "string")
  private User user;

  @Column(name = "lessons_answer_created_at")
  @JsonProperty("lessons_answer_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_answer_updated_at")
  @JsonProperty("lessons_answer_updated_at")
  @NotNull
  private Instant updated = now();

  @Column(name = "lessons_answer_positive")
  @JsonProperty("lessons_answer_positive")
  private String positive;

  @Column(name = "lessons_answer_negative")
  @JsonProperty("lessons_answer_negative")
  private String negative;

  @Column(name = "lessons_answer_score")
  @JsonProperty("lessons_answer_score")
  @NotNull
  private Integer score;

  // region transient
  @JsonProperty("lessons_answer_exercise")
  public String getExercise() {
    if (getQuestion() == null || getQuestion().getCategory() == null) {
      return null;
    }
    Exercise exercise = getQuestion().getCategory().getExercise();
    return exercise != null ? exercise.getId() : null;
  }

  // endregion

  @Override
  public boolean isUserHasAccess(User user) {
    if (getQuestion() == null) {
      return user.isAdmin();
    }
    return getQuestion().isUserHasAccess(user);
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
