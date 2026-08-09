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
 * Reusable phishing email template (a "Component", like {@link Channel}). Holds the lure email
 * subject/body, optional sender identity override and a tracking-pixel toggle. Sent through the
 * platform's global SMTP by the internal phishing injector.
 */
@Getter
@Setter
@Entity
@Table(name = "phishing_email_templates")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PhishingEmailTemplate implements TenantBase {

  @Id
  @Column(name = "phishing_email_template_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("phishing_email_template_id")
  @NotBlank
  private String id;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "phishing_email_template_created_at")
  @JsonProperty("phishing_email_template_created_at")
  @NotNull
  private Instant createdAt = now();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "phishing_email_template_updated_at")
  @JsonProperty("phishing_email_template_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phishing_email_template_name")
  @JsonProperty("phishing_email_template_name")
  @NotBlank
  private String name;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phishing_email_template_description")
  @JsonProperty("phishing_email_template_description")
  private String description;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phishing_email_template_subject")
  @JsonProperty("phishing_email_template_subject")
  @NotBlank
  private String subject;

  @Column(name = "phishing_email_template_html_body", columnDefinition = "text")
  @JsonProperty("phishing_email_template_html_body")
  private String htmlBody;

  @Column(name = "phishing_email_template_text_body", columnDefinition = "text")
  @JsonProperty("phishing_email_template_text_body")
  private String textBody;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phishing_email_template_from_name")
  @JsonProperty("phishing_email_template_from_name")
  private String fromName;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phishing_email_template_from_email")
  @JsonProperty("phishing_email_template_from_email")
  private String fromEmail;

  @Column(name = "phishing_email_template_add_tracking_pixel")
  @JsonProperty("phishing_email_template_add_tracking_pixel")
  private boolean addTrackingPixel = true;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PHISHING_EMAIL_TEMPLATE;

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
