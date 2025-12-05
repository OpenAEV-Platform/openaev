package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Setter
@Getter
@Entity
@Table(name = "edges")
public class Edge {

  @Id
  @Column(name = "edge_id")
  private String id;

  @Column(name = "step_parent_id")
  private String parentId;

  @Column(name = "step_children_id")
  private String stepChildrenId;

  @CreationTimestamp
  @Column(name = "edge_created_at")
  @JsonProperty("dependency_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "edge_updated_at")
  @JsonProperty("dependency_updated_at")
  private Instant updateDate;
}
