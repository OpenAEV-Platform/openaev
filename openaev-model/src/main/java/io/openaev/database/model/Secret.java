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
import lombok.Data;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "secrets")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "secret_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Secret implements TenantBase {

  @Id
  @Column(name = "secret_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("secret_id")
  private String id;

  @Column(name = "secret_type", insertable = false, updatable = false)
  @JsonProperty("secret_type")
  @Setter(NONE)
  private String type;

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
    HASH;

    public static final String USERNAME_PASSWORD_VALUE = "USERNAME_PASSWORD";
    public static final String HASH_VALUE = "HASH";
  }
}
