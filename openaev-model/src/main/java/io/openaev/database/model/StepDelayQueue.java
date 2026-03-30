package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.*;

@Entity
@Table(name = "steps_delay_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class StepDelayQueue implements Base {
  @Id
  @Column(name = "steps_delay_queue_id")
  @JsonProperty("steps_delay_queue_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the deferred step action")
  private String id;

  @Column(name = "steps_delay_queue_input")
  @JsonProperty("steps_delay_queue_input")
  @Schema(description = "Input passed to the step")
  private String input;

  @Column(name = "steps_delay_queue_now")
  @JsonProperty("steps_delay_queue_now")
  @Schema(description = "Timestamp when the deferred action was enqueued")
  private Instant now;

  @Column(name = "steps_delay_queue_goal")
  @JsonProperty("steps_delay_queue_goal")
  @Schema(description = "Target execution timestamp after the delay")
  private Instant goal;

  @Column(name = "steps_delay_queue_delay")
  @JsonProperty("steps_delay_queue_delay")
  @Schema(description = "Delay in milliseconds before the action step is executed")
  private Long delay;

  @ManyToOne
  @JoinColumn(name = "steps_delay_queue_step_template_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  @Schema(description = "Step template to execute when the goal time is reached")
  private Step stepTemplate;

  @ManyToOne
  @JoinColumn(name = "steps_delay_queue_workflow_run_id")
  @Schema(description = "Workflow run that owns this deferred action")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Workflow workflowRun;

  @Column(name = "steps_delay_queue_created_at")
  @JsonProperty("steps_delay_queue_created_at")
  @CreationTimestamp
  @Schema(description = "Timestamp when the action step delayed was created")
  private Instant createdAt;

  @Column(name = "steps_delay_queue_updated_at")
  @JsonProperty("steps_delay_queue_updated_at")
  @UpdateTimestamp
  @Schema(description = "Timestamp when the action step delayed was last updated")
  private Instant updatedAt;
}
