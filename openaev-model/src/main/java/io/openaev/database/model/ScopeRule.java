package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scope_rules")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScopeRule implements Base {

  @Id
  @UuidGenerator
  @Column(name = "scope_rule_id")
  @JsonProperty("scope_rule_id")
  @NotBlank
  private String id;

  @Column(name = "scope_rule_selected_mode")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("scope_rule_selected_mode")
  private ScopeRuleSelectedMode selectedMode;

  @Column(name = "scope_rule_source")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("scope_rule_source")
  private ScopeRuleSource ruleSource;

  @Column(name = "scope_rule_value")
  @JsonProperty("scope_rule_value")
  private String ruleValue;

  @Column(name = "scope_rule_value_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @JsonProperty("scope_rule_type")
  private ScopeRuleValueType valueType;

  @CreationTimestamp
  @Column(name = "scope_rule_created_at")
  @JsonProperty("scope_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "scope_rule_updated_at")
  @JsonProperty("scope_updated_at")
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scope_id")
  @JsonIgnore
  private Scope scope;
}
