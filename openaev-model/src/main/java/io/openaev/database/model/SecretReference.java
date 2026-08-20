package io.openaev.database.model;

import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "secret_references")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "secret_reference_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
// secret_references is fully on v2 (inspector + can_access_tenant); no v1 @Filter
public class SecretReference implements TenantBase {

  public enum SECRET_REFERENCE_TYPE {
    CREDENTIAL;

    public static final String CREDENTIAL_VALUE = "CREDENTIAL";
  }

  public enum SECRET_STATUS {
    ACTIVE,
    INACTIVE,
    UNSET;
  }

  @Id
  @Column(name = "secret_reference_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("secret_reference_id")
  private String id;

  @Column(name = "secret_reference_type", insertable = false, updatable = false)
  @JsonProperty("secret_reference_type")
  @Enumerated(EnumType.STRING)
  @Setter(NONE)
  private SECRET_REFERENCE_TYPE type;

  @Column(name = "secret_reference_name")
  @JsonProperty("secret_reference_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "secret_reference_description")
  @JsonProperty("secret_reference_description")
  private String description;

  @Column(name = "secret_reference_connector_instance_id")
  @JsonProperty("secret_reference_connector_instance_id")
  @NotBlank
  private String connectorInstanceId;

  @Column(name = "secret_reference_location")
  @JsonProperty("secret_reference_location")
  private String location;

  @JsonProperty("secret_reference_status")
  @Queryable(filterable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "secret_reference_status", nullable = false)
  private SECRET_STATUS status = SECRET_STATUS.UNSET;

  @ManyToOne
  @JoinColumn(name = "secret_reference_created_by")
  @JsonProperty("secret_reference_created_by")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Queryable(filterable = true, dynamicValues = true, path = "createdBy.id")
  private User createdBy;

  @Column(name = "secret_reference_created_at")
  @JsonProperty("secret_reference_created_at")
  @Queryable(filterable = true, sortable = true)
  @NotNull
  @CreationTimestamp
  @AuditStateIgnore
  private Instant createdAt = now();

  @Column(name = "secret_reference_updated_at")
  @JsonProperty("secret_reference_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Column(name = "secret_reference_last_verified_at")
  @JsonProperty("secret_reference_last_verified_at")
  @Queryable(filterable = true, sortable = true)
  private Instant lastVerifiedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "secret_reference_tags",
      joinColumns = @JoinColumn(name = "secret_reference_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonProperty("secret_reference_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  @Fetch(FetchMode.SUBSELECT)
  private Set<Tag> tags = new HashSet<>();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;
}
