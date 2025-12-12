package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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

  @Column(name = "security_coverage_external_url", nullable = false)
  @JsonProperty("security_coverage_external_url")
  private String externalUrl;

  @Column(name = "security_coverage_name", nullable = false)
  @JsonProperty("security_coverage_name")
  @NotBlank
  private String name;

  @Column(name = "security_coverage_description")
  @JsonProperty("security_coverage_description")
  private String description;

  @Column(name = "security_coverage_scheduling", nullable = false)
  @JsonProperty("security_coverage_scheduling")
  private String scheduling; // duration

  @Column(name = "security_coverage_period_start")
  @JsonProperty("security_coverage_period_start")
  private Instant periodStart;

  @Column(name = "security_coverage_period_end")
  @JsonProperty("security_coverage_period_end")
  private Instant periodEnd;

  @Type(ListArrayType.class)
  @Column(name = "security_coverage_labels", columnDefinition = "text[]")
  @JsonProperty("security_coverage_labels")
  private Set<String> labels = new HashSet<>();

  @Type(JsonType.class)
  @Column(name = "security_coverage_attack_pattern_refs", columnDefinition = "jsonb")
  @JsonProperty("security_coverage_attack_pattern_refs")
  private Set<StixRefToExternalRef> attackPatternRefs;

  @Type(JsonType.class)
  @Column(name = "security_coverage_content", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("security_coverage_content")
  private String content;

  @Column(
      name = "security_coverage_bundle_hash_md5",
      nullable = false,
      unique = true,
      length = 32 // MD5 produces a 32-character hex string
      )
  @JsonIgnore
  private String bundleHashMd5;

  @Type(JsonType.class)
  @Column(name = "security_coverage_vulnerabilities_refs", columnDefinition = "jsonb")
  @JsonProperty("security_coverage_vulnerabilities_refs")
  private Set<StixRefToExternalRef> vulnerabilitiesRefs;

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
