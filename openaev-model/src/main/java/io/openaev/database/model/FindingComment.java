package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.AuditDiffTracked;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A comment left by a user on a {@link Finding}.
 *
 * <p>{@code @AuditDiffTracked} captures a before/after snapshot of scalar fields (i.e. {@code
 * content}) around {@code @PreUpdate}, which {@code AccessControlAuditLogAspect} then attaches to
 * the edit audit event automatically - this is what supplies "previous content, new content" for
 * DORA/TIBER-EU traceability, with no custom logging code needed. On delete, the full comment
 * content is preserved the same way: {@code FindingCommentApi#deleteFindingComment} returns the
 * deleted entity (not {@code Void}), which the same aspect serializes as the event's output.
 */
@Data
@Entity
@Table(name = "finding_comments")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@AuditDiffTracked
public class FindingComment implements TenantBase {

  @Id
  @Column(name = "finding_comment_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("finding_comment_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_comment_finding_id", updatable = false, nullable = false)
  @JsonProperty("finding_comment_finding_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  @Queryable(filterable = true, path = "finding.id")
  @NotNull
  private Finding finding;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_comment_author_id", updatable = false, nullable = false)
  @JsonProperty("finding_comment_author_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  @NotNull
  private User author;

  // 4000 chars mirrors the DB CHECK constraint added in
  // V6_20260730140000000__Add_finding_comments (deliberate new baseline, no existing precedent
  // reused - see migration comment for rationale).
  @Column(name = "finding_comment_content", nullable = false)
  @JsonProperty("finding_comment_content")
  @NotBlank
  @Size(max = 4000)
  private String content;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @CreationTimestamp
  @Column(name = "finding_comment_created_at", updatable = false, nullable = false)
  @JsonProperty("finding_comment_created_at")
  @NotNull
  private Instant creationDate = now();

  // Stays null until the first edit - the frontend uses this presence (not a boolean flag) to
  // decide whether to show the "(edited)" indicator.
  @Column(name = "finding_comment_updated_at")
  @JsonProperty("finding_comment_updated_at")
  private Instant updateDate;
}
