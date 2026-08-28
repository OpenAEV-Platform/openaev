package io.openaev.database.model.autonomous;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A reusable objective template for autonomous runs (e.g. "Reach the domain controller", "Prove
 * data exfiltration", "Validate EDR detections"). Built-ins are seeded per tenant and admins can
 * add their own. The gallery in the run-creation UI reads these.
 */
@Getter
@Setter
@Entity
@Table(name = "autonomous_objective_templates")
@EntityListeners(TenantBaseListener.class)
public class AutonomousObjectiveTemplate implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "autonomous_objective_template_id")
  @JsonProperty("autonomous_objective_template_id")
  @Schema(description = "ID of the objective template")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @NotNull
  @Column(name = "autonomous_objective_template_key", nullable = false)
  @JsonProperty("autonomous_objective_template_key")
  @Schema(description = "Stable key (unique per tenant) for built-in identification")
  private String key;

  @NotNull
  @Column(name = "autonomous_objective_template_label", nullable = false)
  @JsonProperty("autonomous_objective_template_label")
  @Schema(description = "Human label shown in the gallery")
  private String label;

  @Column(name = "autonomous_objective_template_description", columnDefinition = "text")
  @JsonProperty("autonomous_objective_template_description")
  @Schema(description = "Short description of what the objective does")
  private String description;

  @Column(name = "autonomous_objective_template_icon")
  @JsonProperty("autonomous_objective_template_icon")
  @Schema(description = "Icon key used by the frontend gallery card")
  private String icon;

  @NotNull
  @Column(
      name = "autonomous_objective_template_prompt",
      nullable = false,
      columnDefinition = "text")
  @JsonProperty("autonomous_objective_template_prompt")
  @Schema(description = "Objective prompt handed to the orchestrator")
  private String prompt;

  @Column(name = "autonomous_objective_template_kill_chain_focus")
  @JsonProperty("autonomous_objective_template_kill_chain_focus")
  @Schema(description = "Optional kill-chain phase focus hint")
  private String killChainFocus;

  @NotNull
  @Column(name = "autonomous_objective_template_scope_mode", nullable = false)
  @JsonProperty("autonomous_objective_template_scope_mode")
  @Schema(
      description =
          "Whether the objective is environment-wide (operates over the whole authorized scope, no"
              + " target choice needed) or target-dependent (needs a specific target/asset the"
              + " operator picks up front or the orchestrator asks for). One of: environment,"
              + " target.")
  private String scopeMode = "environment";

  @Column(name = "autonomous_objective_template_builtin", nullable = false)
  @JsonProperty("autonomous_objective_template_builtin")
  @Schema(description = "Whether this is a seeded built-in template")
  private boolean builtin;

  @Column(name = "autonomous_objective_template_enabled", nullable = false)
  @JsonProperty("autonomous_objective_template_enabled")
  @Schema(description = "Whether the template is offered in the gallery")
  private boolean enabled = true;

  @Column(name = "autonomous_objective_template_order")
  @JsonProperty("autonomous_objective_template_order")
  @Schema(description = "Display order in the gallery")
  private int order;

  @CreationTimestamp
  @Column(name = "autonomous_objective_template_created_at", updatable = false)
  @JsonProperty("autonomous_objective_template_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "autonomous_objective_template_updated_at")
  @JsonProperty("autonomous_objective_template_updated_at")
  @Schema(description = "Update date")
  private Instant updatedAt;
}
