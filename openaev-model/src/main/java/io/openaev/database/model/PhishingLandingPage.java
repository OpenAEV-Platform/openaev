package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Reusable, publicly-accessible phishing landing page (a "Component", like {@link Channel}). Each
 * landing page auto-synthesizes its own {@link InjectorContract} (a Threat Arsenal action) so a
 * phishing campaign is a normal inject targeting Teams/Players. Themed with the platform theme and
 * per-page branding; renders sanitized HTML/CSS and captures submitted credentials as Findings.
 */
@Getter
@Setter
@Entity
@Table(name = "phishing_landing_pages")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PhishingLandingPage implements TenantBase {

  @Id
  @Column(name = "phishing_landing_page_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("phishing_landing_page_id")
  @NotBlank
  private String id;

  @Column(name = "phishing_landing_page_created_at")
  @JsonProperty("phishing_landing_page_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "phishing_landing_page_updated_at")
  @JsonProperty("phishing_landing_page_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "phishing_landing_page_name")
  @JsonProperty("phishing_landing_page_name")
  @NotBlank
  private String name;

  @Column(name = "phishing_landing_page_description")
  @JsonProperty("phishing_landing_page_description")
  private String description;

  @Column(name = "phishing_landing_page_html", columnDefinition = "text")
  @JsonProperty("phishing_landing_page_html")
  private String html;

  @Column(name = "phishing_landing_page_css", columnDefinition = "text")
  @JsonProperty("phishing_landing_page_css")
  private String css;

  @Column(name = "phishing_landing_page_capture_submitted_data")
  @JsonProperty("phishing_landing_page_capture_submitted_data")
  private boolean captureSubmittedData = true;

  @Column(name = "phishing_landing_page_capture_passwords")
  @JsonProperty("phishing_landing_page_capture_passwords")
  private boolean capturePasswords = true;

  @Column(name = "phishing_landing_page_redirect_url")
  @JsonProperty("phishing_landing_page_redirect_url")
  private String redirectUrl;

  @Column(name = "phishing_landing_page_primary_color_dark")
  @JsonProperty("phishing_landing_page_primary_color_dark")
  private String primaryColorDark;

  @Column(name = "phishing_landing_page_primary_color_light")
  @JsonProperty("phishing_landing_page_primary_color_light")
  private String primaryColorLight;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_landing_page_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_landing_page_logo_dark")
  @Schema(implementation = String.class)
  private Document logoDark;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_landing_page_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_landing_page_logo_light")
  @Schema(implementation = String.class)
  private Document logoLight;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PHISHING_LANDING_PAGE;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  public List<Document> getLogos() {
    List<Document> logos = new ArrayList<>();
    if (logoLight != null) {
      logos.add(logoLight);
    }
    if (logoDark != null) {
      logos.add(logoDark);
    }
    return logos;
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
