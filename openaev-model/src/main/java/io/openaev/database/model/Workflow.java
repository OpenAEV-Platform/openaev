package io.openaev.database.model;

import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "workflows")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Workflow implements Base {

  @Id
  @Column(name = "workflow_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Schema(description = "ID of the workflow")
  private String id;

  @Column(name = "workflow_status")
  @Enumerated(EnumType.STRING)
  @Schema(description = "Status of the workflow (TEMPLATE, RUN, STOP, END)")
  private WORKFLOW_STATUS status;

  @Min(0)
  @Column(name = "workflow_version")
  @Schema(description = "Version of the workflow, incremented at each launch if edited")
  private int version;

  @Column(name = "workflow_is_edited")
  @Schema(description = "Workflow template is edited")
  private boolean isEdited;

  @CreationTimestamp
  @Column(name = "workflow_created_at")
  @Schema(description = "Creation date")
  private Instant workflowCreatedAt;

  @UpdateTimestamp
  @Column(name = "workflow_updated_at")
  @Schema(description = "Update date")
  private Instant workflowUpdatedAt;

  // JOIN
  @JoinColumn(name = "workflow_template_id")
  @ManyToOne(fetch = FetchType.LAZY)
  private Workflow workflowTemplate;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "id")
  private List<Workflow> workflowExecuted;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "workflow")
  private List<Step> steps;

  //
  @OneToOne(mappedBy = "workflow")
  private Exercise simulation;

  @OneToOne(mappedBy = "workflow")
  private Scenario scenario;
}
