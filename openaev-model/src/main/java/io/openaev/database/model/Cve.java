package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdListDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "cves")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Cve implements Base {

  public enum VulnerabilityStatus {
    ANALYZED,
    DEFERRED,
    MODIFIED,
  }

  @Id
  @Column(name = "cve_id")
  @JsonProperty("cve_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @Column(name = "cve_external_id")
  @JsonProperty("cve_external_id")
  @NotBlank
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String externalId;

  @Column(name = "cve_source_identifier")
  @JsonProperty("cve_source_identifier")
  private String sourceIdentifier;

  @Column(name = "cve_published")
  @JsonProperty("cve_published")
  @Queryable(sortable = true)
  private Instant published;

  @Enumerated(EnumType.STRING)
  @Column(name = "cve_vuln_status")
  @JsonProperty("cve_vuln_status")
  private VulnerabilityStatus vulnStatus;

  @Column(name = "cve_description")
  @JsonProperty("cve_description")
  private String description;

  @Column(name = "cve_remediation")
  @JsonProperty("cve_remediation")
  private String remediation;

  @Column(name = "cve_cisa_exploit_add")
  @JsonProperty("cve_cisa_exploit_add")
  private Instant cisaExploitAdd;

  @Column(name = "cve_cisa_action_due")
  @JsonProperty("cve_cisa_action_due")
  private Instant cisaActionDue;

  @Column(name = "cve_cisa_required_action")
  @JsonProperty("cve_cisa_required_action")
  private String cisaRequiredAction;

  @Column(name = "cve_cisa_vulnerability_name")
  @JsonProperty("cve_cisa_vulnerability_name")
  private String cisaVulnerabilityName;

  @ElementCollection
  @CollectionTable(name = "cve_reference_urls", joinColumns = @JoinColumn(name = "cve_id"))
  @Column(name = "cve_reference_url")
  @JsonProperty("cve_reference_urls")
  private List<String> referenceUrls = new ArrayList<>();

  @NotNull
  @Column(name = "cve_cvss_v31", precision = 3, scale = 1)
  @JsonProperty("cve_cvss_v31")
  @Queryable(searchable = true, filterable = true, sortable = true)
  private BigDecimal cvssV31;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany
  @JoinTable(
      name = "cves_cwes",
      joinColumns = @JoinColumn(name = "cve_id"),
      inverseJoinColumns = @JoinColumn(name = "cwe_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("cves_cwes")
  private List<Cwe> cwes = new ArrayList<>();

  // -- AUDIT --

  @CreationTimestamp
  @Queryable(filterable = true, sortable = true, label = "created at")
  @Column(name = "cve_created_at", updatable = false)
  @JsonProperty("cve_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Queryable(filterable = true, sortable = true, label = "updated at")
  @Column(name = "cve_updated_at")
  @JsonProperty("cve_updated_at")
  private Instant updateDate;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.CVE;
}
