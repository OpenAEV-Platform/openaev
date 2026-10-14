package io.openaev.database.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Global (no step template) or local (per step template) execution state of a workflow run. The
 * state content lives in {@code workflow_state_entries} rows (ADR-011); the legacy {@code
 * workflow_state_entries} JSONB column is no longer mapped and is dropped in the next release.
 */
@Entity
@Table(
    name = "workflow_states",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"workflow_execution_id", "workflow_step_template_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowState implements Base {

  @Id
  @Column(name = "workflow_state_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the workflow")
  private String id;

  @Column(name = "workflow_state_created_at", updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "workflow_state_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_execution_id", nullable = false)
  private Workflow workflowExecution;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_step_template_id") // Nullable for Global
  private Step stepTemplate;
}
