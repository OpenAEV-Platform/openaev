package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "tenant_xtmhub_registrations")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TenantXtmHubRegistration implements TenantBase {

  @Id
  @Column(name = "registration_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("registration_id")
  private String id;

  @Column(name = "registration_token")
  @JsonProperty("registration_token")
  private String token;

  @Column(name = "registration_date")
  @JsonProperty("registration_date")
  private LocalDateTime registrationDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "registration_status")
  @JsonProperty("registration_status")
  private XtmHubRegistrationStatus registrationStatus;

  @Column(name = "registration_user_id")
  @JsonProperty("registration_user_id")
  private String registrationUserId;

  @Column(name = "registration_user_name")
  @JsonProperty("registration_user_name")
  private String registrationUserName;

  @Column(name = "registration_last_connectivity_check")
  @JsonProperty("registration_last_connectivity_check")
  private LocalDateTime lastConnectivityCheck;

  @Column(name = "registration_connectivity_email_eligible")
  @JsonProperty("registration_connectivity_email_eligible")
  private boolean connectivityEmailEligible = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.XTM_HUB_REGISTRATION;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id != null && id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
