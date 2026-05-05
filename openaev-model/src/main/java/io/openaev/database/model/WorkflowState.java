package io.openaev.database.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

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

  @Type(JsonType.class)
  @Column(name = "workflow_state_entries", columnDefinition = "jsonb")
  // @Convert(converter = WorkflowStateEntriesConverter.class)
  private String entries; // This maps to StateEntries object

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
