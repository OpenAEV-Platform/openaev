package io.openaev.rest.exercise.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(NON_NULL)
public class ExerciseSimple {

  @Schema(description = "Exercise Id")
  @JsonProperty("exercise_id")
  @NotBlank
  private String id;

  @Schema(description = "Exercise Name")
  @JsonProperty("exercise_name")
  @NotBlank
  private String name;

  @Schema(description = "Exercise status")
  @JsonProperty("exercise_status")
  @Enumerated(EnumType.STRING)
  private ExerciseStatus status;

  @Schema(description = "Exercise Subtitle")
  @JsonProperty("exercise_subtitle")
  private String subtitle;

  @Schema(description = "Exercise Category")
  @JsonProperty("exercise_category")
  private String category;

  @Schema(description = "Exercise Start Date")
  @JsonProperty("exercise_start_date")
  private Instant start;

  @Schema(description = "Exercise Update Date")
  @JsonProperty("exercise_updated_at")
  private Instant updatedAt = now();

  @Schema(description = "Tags")
  @JsonProperty("exercise_tags")
  private Set<String> tagIds = new HashSet<>();

  @JsonIgnore private String[] injectIds;

  // COMPUTED ATTRIBUTES

  @JsonProperty("exercise_global_score")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();

  @JsonProperty("exercise_targets")
  private List<TargetSimple> targets = new ArrayList<>();
}
