package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A customer-owned web hostname (e.g. {@code security.acme.com}) pointed at the platform by a CNAME
 * or A record, used to serve phishing landing pages under a benign, branded URL like {@code
 * https://security.acme.com/auth/&lt;token&gt;} instead of the platform's own domain.
 *
 * <p>A domain must pass DNS ownership verification (a TXT challenge record) before it can be used:
 * only a {@link CustomDomainStatus#VERIFIED} domain is offered to landing pages and answered by the
 * public {@code domain-check} endpoint that fronts on-demand TLS. The hostname is globally unique
 * so an inbound request can be mapped to exactly one tenant without ambiguity; because that mapping
 * is consulted from unauthenticated public requests, the two public lookups use native,
 * tenant-filter bypassing queries while all admin CRUD stays tenant-scoped through the Hibernate
 * {@code tenantFilter}.
 */
@Getter
@Setter
@Entity
@Table(name = "custom_domains")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CustomDomain implements TenantBase {

  public enum CustomDomainStatus {
    /** Created, DNS challenge not yet satisfied. */
    PENDING,
    /** TXT ownership challenge validated; the domain can be linked and served. */
    VERIFIED,
    /** A verification attempt ran but the challenge was not found. */
    FAILED,
  }

  @Id
  @Column(name = "custom_domain_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("custom_domain_id")
  @NotBlank
  private String id;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "custom_domain_created_at")
  @JsonProperty("custom_domain_created_at")
  @NotNull
  private Instant createdAt = now();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "custom_domain_updated_at")
  @JsonProperty("custom_domain_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "custom_domain_hostname", unique = true)
  @JsonProperty("custom_domain_hostname")
  @NotBlank
  private String hostname;

  @Queryable(filterable = true, sortable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "custom_domain_status")
  @JsonProperty("custom_domain_status")
  @NotNull
  private CustomDomainStatus status = CustomDomainStatus.PENDING;

  /** Random secret the customer publishes as a TXT record to prove ownership of the hostname. */
  @Column(name = "custom_domain_verification_token")
  @JsonProperty("custom_domain_verification_token")
  @NotBlank
  private String verificationToken;

  @Column(name = "custom_domain_verified_at")
  @JsonProperty("custom_domain_verified_at")
  private Instant verifiedAt;

  @Column(name = "custom_domain_last_checked_at")
  @JsonProperty("custom_domain_last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "custom_domain_last_error", columnDefinition = "text")
  @JsonProperty("custom_domain_last_error")
  private String lastError;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.TENANT_SETTING;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
