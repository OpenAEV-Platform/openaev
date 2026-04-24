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
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Objects;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entity representing a custom variable for exercises and scenarios.
 *
 * <p>Variables provide a templating mechanism that allows dynamic content substitution in inject
 * content, email templates, and other text fields. They support:
 *
 * <ul>
 *   <li>Workflow-scoped variables (specific to one exercise)
 *   <li>String and Object value types
 * </ul>
 *
 * <p>Variable keys follow a snake_case naming convention (e.g., {@code company_name}, {@code
 * target_ip}) and are referenced in templates using a specific syntax.
 *
 * @see Workflow
 */
@Data
@Entity
@EntityListeners(ModelBaseListener.class)
@Table(name = "scope_variables")
public class ScopeVariable implements Base {

  /** Types of variable values. */
  public enum VariableType {
    /** Simple string value. */
    @JsonProperty("String")
    String,

    /** Complex object/JSON value. */
    @JsonProperty("Object")
    Object,
  }

  @Id
  @Column(name = "scope_variable_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("scope_variable_id")
  @NotBlank
  private String id;

  @Column(name = "scope_variable_key")
  @JsonProperty("scope_variable_key")
  @NotBlank
  @Pattern(regexp = "^[a-z_]+$")
  private String key;

  @Column(name = "scope_variable_value")
  @JsonProperty("scope_variable_value")
  private String value;

  @Column(name = "scope_variable_description")
  @JsonProperty("scope_variable_description")
  private String description;

  @Column(name = "scope_variable_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("scope_variable_type")
  @NotNull
  private VariableType type = VariableType.String;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scope_variable_workflow")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("scope_variable_workflow")
  @Schema(implementation = String.class)
  private Workflow workflow;

  // -- AUDIT --

  @Column(name = "scope_variable_created_at")
  @JsonProperty("scope_variable_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "scope_variable_updated_at")
  @JsonProperty("scope_variable_updated_at")
  @NotNull
  private Instant updatedAt = now();

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
