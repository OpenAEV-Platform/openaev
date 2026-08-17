package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One append-only row PER event on a {@link Finding}: either a triage status transition (including
 * reverts) or a manual archive/un-archive action - see {@link FindingHistoryActionType}. Rows are
 * never updated or overwritten - {@link FindingTriage} always holds the current triage status, and
 * {@code Finding#archivedAt} always holds the current archive state.
 *
 * <p>{@code finding} is a direct FK to {@code Finding} (not to {@code FindingTriage}), per explicit
 * product spec, so history survives independently of the current-status row's lifecycle.
 *
 * <p>{@code fromStatus}/{@code toStatus} are only populated for {@code TRIAGE_CHANGE} rows; they
 * are {@code null} for {@code ARCHIVE}/{@code UNARCHIVE} rows, which have no triage transition to
 * describe.
 *
 * <p>{@code actor} is nullable: a {@code null} actor represents an automatic system-triggered
 * transition (re-detection auto-reset to UNTRIAGED). No exact precedent for a "System actor" exists
 * elsewhere in this codebase; the output DTO renders this as "System" when null - this is a
 * deliberate, disclosed design choice for this feature, not copied from an existing pattern.
 */
@Data
@Entity
@Table(name = "finding_triage_histories")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class FindingTriageHistory implements TenantBase {

  @Id
  @Column(name = "finding_triage_history_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("finding_triage_history_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_triage_history_finding_id", updatable = false, nullable = false)
  @JsonProperty("finding_triage_history_finding_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  @NotNull
  private Finding finding;

  // Nullable since V6_20260817124500000__Add_finding_triage_history_action: only populated for
  // TRIAGE_CHANGE rows, null for ARCHIVE/UNARCHIVE rows.
  @Column(name = "finding_triage_history_from_status", updatable = false)
  @JsonProperty("finding_triage_history_from_status")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Triage status before this transition (TRIAGE_CHANGE rows only)")
  private FindingTriageStatus fromStatus;

  // Nullable since V6_20260817124500000__Add_finding_triage_history_action: only populated for
  // TRIAGE_CHANGE rows, null for ARCHIVE/UNARCHIVE rows.
  @Column(name = "finding_triage_history_to_status", updatable = false)
  @JsonProperty("finding_triage_history_to_status")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Triage status after this transition (TRIAGE_CHANGE rows only)")
  private FindingTriageStatus toStatus;

  // Discriminates a plain triage transition from a manual archive/un-archive action. Defaults to
  // TRIAGE_CHANGE to match this entity's original (pre-archive-history) sole purpose.
  @NotNull
  @Column(name = "finding_triage_history_action", updatable = false, nullable = false)
  @JsonProperty("finding_triage_history_action")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "What kind of event this row records")
  private FindingHistoryActionType actionType = FindingHistoryActionType.TRIAGE_CHANGE;

  // 4000 chars reuses the baseline established for finding_comment_content
  // (V6_20260730140000000__Add_finding_comments); 10 chars is the min length required by product
  // spec ("justification (min 10 chars, enforced)"). Both are enforced at the DB level too, see
  // the migration's CHECK constraint.
  @Column(name = "finding_triage_history_justification", updatable = false, nullable = false)
  @JsonProperty("finding_triage_history_justification")
  @NotBlank
  @Size(min = 10, max = 4000)
  private String justification;

  // Nullable: null = System (automatic re-detection reset). See class-level javadoc.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_triage_history_actor_id", updatable = false)
  @JsonProperty("finding_triage_history_actor_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  private User actor;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @CreationTimestamp
  @Column(name = "finding_triage_history_created_at", updatable = false, nullable = false)
  @JsonProperty("finding_triage_history_created_at")
  @NotNull
  private Instant creationDate = now();
}
