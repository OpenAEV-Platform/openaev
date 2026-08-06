package io.openaev.database.model.autonomous;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single entry on an autonomous run's decision timeline (narration, decision, tool action,
 * capability gap, question, proof...). Rows are append-only and monotonically sequenced per run so
 * the UI reads them as a delta cursor alongside the attack-path version nudge.
 */
@Getter
@Setter
@Entity
@Table(name = "autonomous_events")
@EntityListeners(TenantBaseListener.class)
public class AutonomousEvent implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "autonomous_event_id")
  @JsonProperty("autonomous_event_id")
  @Schema(description = "ID of the event")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "autonomous_event_run_id", nullable = false)
  @JsonProperty("autonomous_event_run_id")
  @Schema(description = "Autonomous run this event belongs to")
  private String runId;

  @Column(name = "autonomous_event_sequence", nullable = false)
  @JsonProperty("autonomous_event_sequence")
  @Schema(description = "Monotonic per-run ordering cursor")
  private long sequence;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "autonomous_event_type", nullable = false)
  @JsonProperty("autonomous_event_type")
  @Schema(description = "Kind of timeline entry")
  private AutonomousEventType type;

  @Column(name = "autonomous_event_title")
  @JsonProperty("autonomous_event_title")
  @Schema(description = "Short human title")
  private String title;

  @Column(name = "autonomous_event_content", columnDefinition = "text")
  @JsonProperty("autonomous_event_content")
  @Schema(description = "Human-readable body / narration")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "autonomous_event_data")
  @JsonProperty("autonomous_event_data")
  @Schema(description = "Structured payload (tool i/o, gap suggestions, proof metadata)")
  private String data;

  @CreationTimestamp
  @Column(name = "autonomous_event_created_at", updatable = false)
  @JsonProperty("autonomous_event_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;
}
