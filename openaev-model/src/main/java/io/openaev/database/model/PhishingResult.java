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
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Per-recipient tracking record for a phishing inject. Holds the opaque tracking token embedded in
 * the lure email and records the open/click/submit lifecycle plus captured metadata. Persisted (the
 * external native-phishing injector kept this in memory only).
 *
 * <p>{@link DynamicUpdate} is required because the open/click/submit transitions run as
 * independent, concurrent public requests that each set a different monotonic timestamp on the same
 * row. With Hibernate's default full-row update, a stale {@code markOpened} committing after {@code
 * markSubmitted} would rewrite {@code clickedAt}/{@code submittedAt} back to null. Emitting an
 * UPDATE for only the columns a transition actually dirties removes that cross-field clobbering: a
 * {@code markOpened} update never references the click/submit columns, so it cannot overwrite them.
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "phishing_results")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PhishingResult implements TenantBase {

  @Id
  @Column(name = "phishing_result_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("phishing_result_id")
  @NotBlank
  private String id;

  @Column(name = "phishing_result_created_at")
  @JsonProperty("phishing_result_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "phishing_result_updated_at")
  @JsonProperty("phishing_result_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "phishing_result_token", unique = true)
  @JsonProperty("phishing_result_token")
  @NotBlank
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_result_inject")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_result_inject")
  @Schema(implementation = String.class)
  private Inject inject;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_result_landing_page")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_result_landing_page")
  @Schema(implementation = String.class)
  private PhishingLandingPage landingPage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_result_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_result_user")
  @Schema(implementation = String.class)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_result_team")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_result_team")
  @Schema(implementation = String.class)
  private Team team;

  @Column(name = "phishing_result_sent_at")
  @JsonProperty("phishing_result_sent_at")
  private Instant sentAt;

  @Column(name = "phishing_result_opened_at")
  @JsonProperty("phishing_result_opened_at")
  private Instant openedAt;

  @Column(name = "phishing_result_clicked_at")
  @JsonProperty("phishing_result_clicked_at")
  private Instant clickedAt;

  @Column(name = "phishing_result_submitted_at")
  @JsonProperty("phishing_result_submitted_at")
  private Instant submittedAt;

  @Column(name = "phishing_result_ip")
  @JsonProperty("phishing_result_ip")
  private String ip;

  @Column(name = "phishing_result_user_agent", columnDefinition = "text")
  @JsonProperty("phishing_result_user_agent")
  private String userAgent;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phishing_result_finding")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("phishing_result_finding")
  @Schema(implementation = String.class)
  private Finding finding;

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
