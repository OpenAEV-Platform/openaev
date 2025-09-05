package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openbas.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "security_coverages")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SecurityCoverage implements Base {

  @Id
  @Column(name = "security_coverage_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @JsonProperty("security_coverage_id")
  private String id;

  @Column(name = "security_coverage_external_id", nullable = false)
  @JsonProperty("security_coverage_external_id")
  private String externalId;

  @Column(name = "security_coverage_name", nullable = false)
  @JsonProperty("security_coverage_name")
  @NotBlank
  private String name;

  @Column(name = "security_coverage_description")
  @JsonProperty("security_coverage_description")
  private String description;

  @Column(name = "security_coverage_scheduling", nullable = false)
  @JsonProperty("security_coverage_scheduling")
  private String scheduling;

  @Column(name = "security_coverage_period_start")
  @JsonProperty("security_coverage_period_start")
  private Instant periodStart;

  @Column(name = "security_coverage_period_end")
  @JsonProperty("security_coverage_period_end")
  private Instant periodEnd;

  @Column(name = "security_coverage_threat_context_ref")
  @JsonProperty("security_coverage_threat_context_ref")
  private String threatContextRef;

  @Type(ListArrayType.class)
  @Column(name = "security_coverage_labels", columnDefinition = "text[]")
  @JsonProperty("security_coverage_labels")
  private List<String> labels = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "security_coverage_attack_pattern_refs", columnDefinition = "jsonb")
  @JsonProperty("security_coverage_attack_pattern_refs")
  private List<StixRefToExternalRef> attackPatternRefs;

  @Type(JsonType.class)
  @Column(name = "security_coverage_content", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("security_coverage_content")
  private String content;

  @Type(JsonType.class)
  @Column(name = "security_coverage_vulnerabilities_refs", columnDefinition = "jsonb")
  @JsonProperty("security_coverage_vulnerabilities_refs")
  private List<StixRefToExternalRef> vulnerabilitiesRefs;

  @OneToOne
  @JoinColumn(name = "security_coverage_scenario")
  @JsonIgnore
  private Scenario scenario;

  @CreationTimestamp
  @Column(name = "security_coverage_created_at", updatable = false)
  @JsonProperty("security_coverage_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "security_coverage_updated_at")
  @JsonProperty("security_coverage_updated_at")
  private Instant updatedAt;
}
