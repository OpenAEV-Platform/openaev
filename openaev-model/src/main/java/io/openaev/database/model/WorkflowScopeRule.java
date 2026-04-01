package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_scope_rules")
@EntityListeners(ModelBaseListener.class)
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowScopeRule implements Base {

  @Id
  @Column(name = "workflow_scope_rule_id")
  @JsonProperty("workflow_scope_rule_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the workflow scope rule")
  private String id;

  @Column(name = "workflow_scope_rule_selected_mode")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("workflow_scope_rule_selected_mode")
  private ScopeRuleSelectedMode selectedMode;

  @Column(name = "workflow_scope_rule_source")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("workflow_scope_rule_source")
  private ScopeRuleSource ruleSource;

  @Column(name = "workflow_scope_rule_value")
  @JsonProperty("workflow_scope_rule_value")
  private String ruleValue;

  @Column(name = "workflow_scope_rule_value_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("workflow_scope_rule_value_type")
  private ScopeRuleValueType valueType;

  @CreationTimestamp
  @Column(name = "workflow_scope_rule_created_at")
  @JsonProperty("workflow_scope_rule_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "workflow_scope_rule_updated_at")
  @JsonProperty("workflow_scope_rule_updated_at")
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_id")
  @JsonIgnore
  private Workflow workflow;

  public static WorkflowScopeRule copyOf(WorkflowScopeRule source, Workflow target) {
    return WorkflowScopeRule.builder()
        .selectedMode(source.getSelectedMode())
        .ruleSource(source.getRuleSource())
        .ruleValue(source.getRuleValue())
        .valueType(source.getValueType())
        .workflow(target)
        .build();
  }
}
