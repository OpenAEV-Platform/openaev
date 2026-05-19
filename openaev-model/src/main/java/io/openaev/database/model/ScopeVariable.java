package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entity representing a custom variable for workflows.
 *
 * <p>Variables provide a templating mechanism that allows dynamic content substitution in inject
 * content. They support:
 *
 * <ul>
 *   <li>Workflow-scoped variables
 * </ul>
 *
 * @see Workflow
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(ModelBaseListener.class)
@Table(name = "scope_variables")
public class ScopeVariable implements Base {

  @Id
  @Column(name = "scope_variable_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("scope_variable_id")
  @NotNull
  private String id;

  @Column(name = "scope_variable_key")
  @JsonProperty("scope_variable_key")
  @NotNull
  private String key;

  @Column(name = "scope_variable_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("scope_variable_type")
  @NotNull
  private ArgumentType type;

  @Column(name = "scope_variable_value")
  @JsonProperty("scope_variable_value")
  private String value;

  @Column(name = "scope_variable_description")
  @JsonProperty("scope_variable_description")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scope_variable_workflow")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("scope_variable_workflow")
  @Schema(implementation = String.class)
  private Workflow workflow;

  // -- AUDIT --

  @Column(name = "scope_variable_created_at", updatable = false)
  @JsonProperty("scope_variable_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "scope_variable_updated_at")
  @JsonProperty("scope_variable_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  /**
   * Creates a detached copy of this variable bound to a different workflow. Used when cloning a
   * workflow template (e.g. scenario → simulation, template → run).
   *
   * @param source the variable to copy
   * @param targetWorkflow the new owner workflow
   * @return a new, unsaved {@link ScopeVariable} with the same field values
   */
  public static ScopeVariable copyOf(ScopeVariable source, Workflow targetWorkflow) {
    ScopeVariable copy = new ScopeVariable();
    copy.setKey(source.getKey());
    copy.setType(source.getType());
    copy.setValue(source.getValue());
    copy.setDescription(source.getDescription());
    copy.setWorkflow(targetWorkflow);
    return copy;
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
