package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(
    name = "edges",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"step_parent_id", "step_child_id"})})
public class Edge {

  @Id
  @Column(name = "edge_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Schema(description = "ID of the edge")
  private String id;

  @CreationTimestamp
  @Column(name = "edge_created_at")
  @JsonProperty("edge_created_at")
  @Schema(description = "Creation date")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "edge_updated_at")
  @JsonProperty("edge_updated_at")
  @Schema(description = "Update date")
  private Instant updateDate;

  // JOIN

  @JoinTable(
      name = "step",
      joinColumns =
          @JoinColumn(
              name = "step_id",
              foreignKey =
                  @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_edge_step_parent")))
  @Column(name = "step_parent_id")
  @Schema(description = "ID of the step parent")
  private String stepParentId;

  @JoinTable(
      name = "step",
      joinColumns =
          @JoinColumn(
              name = "step_id",
              foreignKey =
                  @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_edge_step_child")))
  @Column(name = "step_children_id")
  @Schema(description = "ID of the step child")
  private String stepChildId;

  @JoinTable(
      name = "workflows",
      joinColumns = @JoinColumn(name = "workflow_id"),
      foreignKey = @ForeignKey(value = ConstraintMode.CONSTRAINT, name = "fk_edge_workflow"))
  @Schema(description = "ID of the workflow")
  @Column(name = "edge_workflow_id")
  private String workflowId;

  @JoinTable(
      name = "edges",
      joinColumns =
          @JoinColumn(
              name = "edge_id",
              foreignKey =
                  @ForeignKey(
                      value = ConstraintMode.CONSTRAINT,
                      name = "fk_edge_executed_edge_template")))
  @Schema(description = "ID of the edge template")
  @Column(name = "edge_template_id")
  private String edgeTemplateId;
}
