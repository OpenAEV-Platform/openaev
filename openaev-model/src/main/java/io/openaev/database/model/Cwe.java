package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "cwes")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cwe implements TenantBase {

  @Id
  @Column(name = "cwe_id")
  @JsonProperty("cwe_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @Column(name = "cwe_external_id")
  @JsonProperty("cwe_external_id")
  @NotBlank
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String externalId;

  @Column(name = "cwe_source")
  @JsonProperty("cwe_source")
  private String source;

  @ManyToMany(mappedBy = "cwes")
  private List<Vulnerability> vulnerabilities;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- AUDIT --

  @CreationTimestamp
  @Queryable(filterable = true, sortable = true, label = "created at")
  @Column(name = "cwe_created_at", updatable = false)
  @JsonProperty("cwe_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Queryable(filterable = true, sortable = true, label = "updated at")
  @Column(name = "cwe_updated_at")
  @JsonProperty("cwe_updated_at")
  private Instant updateDate;
}
