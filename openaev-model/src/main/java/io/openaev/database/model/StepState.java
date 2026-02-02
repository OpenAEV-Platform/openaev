package io.openaev.database.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Table(
    name = "steps_states",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"workflow_execution_id", "step_template_id"}))
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepState {
  @Id
  @Column(name = "state_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  // StateEntries as json
  @Type(JsonType.class)
  @Column(name = "step_entries", columnDefinition = "jsonb")
  private String entries;

  @Column(name = "step_state_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "step_state_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  // JOIN
  @OneToOne
  @JoinColumn(name = "workflow_execution_id", unique = true)
  private Workflow workflowExecution;

  @OneToOne
  @JoinColumn(name = "step_template_id", unique = true)
  private Step stepTemplate;
}
