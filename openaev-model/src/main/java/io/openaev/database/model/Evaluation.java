package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

@Setter
@Getter
@Entity
@Table(name = "evaluations")
@EntityListeners(ModelBaseListener.class)
public class Evaluation implements Base {

  @Id
  @Column(name = "evaluation_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("evaluation_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluation_objective")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("evaluation_objective")
  @NotNull
  @Schema(type = "string")
  private Objective objective;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluation_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("evaluation_user")
  @NotNull
  @Schema(type = "string")
  private User user;

  @Column(name = "evaluation_score")
  @JsonProperty("evaluation_score")
  private Long score;

  @Column(name = "evaluation_created_at")
  @JsonProperty("evaluation_created_at")
  @NotNull
  private Instant created = now();

  @Column(name = "evaluation_updated_at")
  @JsonProperty("evaluation_updated_at")
  @NotNull
  private Instant updated = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.EVALUATION;

  @Override
  public boolean isUserHasAccess(User user) {
    if (getObjective() == null) {
      return user.isAdmin();
    }
    return getObjective().isUserHasAccess(user);
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
    if (this.getObjective() == null) {
      return this.getId();
    }
    return this.getObjective().getParentResourceId();
  }

  @JsonIgnore
  public ResourceType getParentResourceType() {
    if (this.getObjective() == null) {
      return ResourceType.EVALUATION;
    }
    return this.getObjective().getParentResourceType();
  }
}
