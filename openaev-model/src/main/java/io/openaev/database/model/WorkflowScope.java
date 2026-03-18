package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "workflow_scopes")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowScope implements Base {

  @Id
  @Column(name = "workflow_scope_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("workflow_scope_id")
  @NotBlank
  private String id;

  @OneToMany(
      mappedBy = "workflowScope",
      fetch = FetchType.LAZY,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @JsonProperty("workflow_scope_rules")
  private List<WorkflowScopeRule> workflowScopeRules = new ArrayList<WorkflowScopeRule>();

  @CreationTimestamp
  @Column(name = "workflow_scope_created_at")
  @JsonProperty("workflow_scope_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "workflow_scope_updated_at")
  @JsonProperty("workflow_scope_updated_at")
  private Instant updatedAt;

  @OneToOne
  @JoinColumn(
      name = "workflow_scope_workflow_configuration",
      referencedColumnName = "workflow_configuration_id")
  @JsonIgnore
  private WorkflowConfiguration workflowConfiguration;

  @JsonIgnore
  public List<WorkflowScopeRule> getWhitelist() {
    return this.workflowScopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.WHITELIST.equals(r.getSelectedMode()))
        .collect(Collectors.toList());
  }

  @JsonIgnore
  public List<WorkflowScopeRule> getBlacklist() {
    return this.workflowScopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.BLACKLIST.equals(r.getSelectedMode()))
        .collect(Collectors.toList());
  }
}
