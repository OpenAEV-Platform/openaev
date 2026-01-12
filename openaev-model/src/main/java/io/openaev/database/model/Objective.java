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
@Table(name = "objectives")
@EntityListeners(ModelBaseListener.class)
public class Objective implements Base {

  @Id
  @Column(name = "objective_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("objective_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "objective_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("objective_exercise")
  @Schema(type = "string")
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "objective_scenario")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("objective_scenario")
  @Schema(type = "string")
  private Scenario scenario;

  @Column(name = "objective_title")
  @JsonProperty("objective_title")
  private String title;

  @Column(name = "objective_description")
  @JsonProperty("objective_description")
  private String description;

  @Column(name = "objective_priority")
  @JsonProperty("objective_priority")
  private Short priority;

  @Column(name = "objective_created_at")
  @JsonProperty("objective_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "objective_updated_at")
  @JsonProperty("objective_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "objective", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("objective_evaluations")
  private List<Evaluation> evaluations = new ArrayList<>();

  // region transient
  @JsonProperty("objective_score")
  public Double getEvaluationAverage() {
    return getEvaluations().stream().mapToDouble(Evaluation::getScore).average().orElse(0D);
  }

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.OBJECTIVE;

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

  @JsonIgnore
  public String getParentResourceId() {
    return this.getScenario() != null
        ? this.getScenario().getId()
        : this.getExercise() != null ? this.getExercise().getId() : this.getId();
  }

  @JsonIgnore
  public ResourceType getParentResourceType() {
    return this.getScenario() != null
        ? ResourceType.SCENARIO
        : this.getExercise() != null ? ResourceType.SIMULATION : ResourceType.OBJECTIVE;
  }
}
