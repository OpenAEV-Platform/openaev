package io.openaev.database.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(ModelBaseListener.class)
public class Step {

  @Id
  @Column(name = "step_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @Column(name = "step_action_class")
  @Enumerated(EnumType.STRING)
  private STEP_ACTION_CLASS stepAction;

  @Type(JsonType.class)
  @Column(name = "step_output", columnDefinition = "jsonb")
  private Map<String, Object> output;

  @Type(JsonType.class)
  @Column(name = "step_input", columnDefinition = "jsonb")
  private Map<String, Object> input;

  @Type(JsonType.class)
  @Column(name = "step_data", columnDefinition = "jsonb")
  private Map<String, Object> data;

  @Column(name = "step_limit_execution") // ? same value or include diff value?
  private int limitExecution;

  @Column(name = "step_condition_excuted")
  private String conditionExecuted;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_status")
  private STEP_STATUS status;

  @Column(name = "step_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "step_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  // JOIN
  @JoinColumn(name = "step_workflow_id")
  @ManyToOne(fetch = FetchType.LAZY)
  private Workflow workflow;

  @JoinColumn(name = "step_template_id")
  @ManyToOne(fetch = FetchType.LAZY)
  private Step stepTemplate;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "id")
  private List<Step> stepExecuted;

  @OneToOne(mappedBy = "id")
  private Condition condition;
}
