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
@Table(name = "scopes")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Scope implements Base {

  @Id
  @UuidGenerator
  @Column(name = "scope_id")
  @JsonProperty("scope_id")
  @NotBlank
  private String id;

  @OneToMany(
      mappedBy = "scope",
      fetch = FetchType.LAZY,
      orphanRemoval = true,
      cascade = CascadeType.ALL)
  @JsonProperty("scope_rules")
  private List<ScopeRule> scopeRules = new ArrayList<ScopeRule>();

  @CreationTimestamp
  @Column(name = "scope_created_at")
  @JsonProperty("scope_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "scope_updated_at")
  @JsonProperty("scope_updated_at")
  private Instant updatedAt;

  @OneToOne
  @JoinColumn(
      name = "scope_chaining_configuration",
      referencedColumnName = "chaining_configuration_id")
  @JsonIgnore
  private ChainingConfiguration chainingConfiguration;

  @JsonIgnore
  public List<ScopeRule> getWhitelist() {
    return this.scopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.WHITELIST.equals(r.getSelectedMode()))
        .collect(Collectors.toList());
  }

  @JsonIgnore
  public List<ScopeRule> getBlacklist() {
    return this.scopeRules.stream()
        .filter(r -> ScopeRuleSelectedMode.BLACKLIST.equals(r.getSelectedMode()))
        .collect(Collectors.toList());
  }
}
