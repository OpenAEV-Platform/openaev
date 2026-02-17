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
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "lessons_template_questions")
@EntityListeners(ModelBaseListener.class)
@Data
public class LessonsTemplateQuestion implements Base {
  @Id
  @Column(name = "lessons_template_question_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("lessonstemplatequestion_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lessons_template_question_category")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("lessons_template_question_category")
  @Schema(type = "string")
  private LessonsTemplateCategory category;

  @Column(name = "lessons_template_question_created_at")
  @JsonProperty("lessons_template_question_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "lessons_template_question_updated_at")
  @JsonProperty("lessons_template_question_updated_at")
  @NotNull
  private Instant updated = now();

  @Column(name = "lessons_template_question_content")
  @JsonProperty("lessons_template_question_content")
  @NotBlank
  private String content;

  @Column(name = "lessons_template_question_explanation")
  @JsonProperty("lessons_template_question_explanation")
  private String explanation;

  @Column(name = "lessons_template_question_order")
  @JsonProperty("lessons_template_question_order")
  @NotNull
  private int order;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }
}
