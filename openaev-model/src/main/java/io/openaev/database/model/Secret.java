package io.openaev.database.model;

import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "secrets")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "secret_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
// secrets is fully on v2 (inspector + can_access_tenant); no v1 @Filter
@ToString(onlyExplicitlyIncluded = true)
public class Secret implements TenantBase {

  @Id
  @Column(name = "secret_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("secret_id")
  @ToString.Include
  private String id;

  @Column(name = "secret_type", insertable = false, updatable = false)
  @JsonProperty("secret_type")
  @Setter(NONE)
  @ToString.Include
  @Enumerated(EnumType.STRING)
  private SECRET_TYPE type;

  @Column(name = "secret_created_at")
  @JsonProperty("secret_created_at")
  @NotNull
  @CreationTimestamp
  @AuditStateIgnore
  private Instant createdAt = now();

  @Column(name = "secret_updated_at")
  @JsonProperty("secret_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  public enum SECRET_TYPE {
    USERNAME_PASSWORD,
    HASH,
    AWS_ACCESS_KEY,
    AWS_ASSUME_ROLE,
    AZURE_SERVICE_PRINCIPAL,
    AZURE_MANAGED_IDENTITY;

    public static final String USERNAME_PASSWORD_VALUE = "USERNAME_PASSWORD";
    public static final String HASH_VALUE = "HASH";
    public static final String AWS_ACCESS_KEY_VALUE = "AWS_ACCESS_KEY";
    public static final String AWS_ASSUME_ROLE_VALUE = "AWS_ASSUME_ROLE";
    public static final String AZURE_SERVICE_PRINCIPAL_VALUE = "AZURE_SERVICE_PRINCIPAL";
    public static final String AZURE_MANAGED_IDENTITY_VALUE = "AZURE_MANAGED_IDENTITY";
  }
}
