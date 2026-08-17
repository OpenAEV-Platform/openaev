package io.openaev.database.model.autonomous;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An operator steering directive injected into a live autonomous run. The orchestrator reads
 * PENDING directives at the start of each decision cycle, so scope, focus, or "avoid host X"
 * instructions take effect without stopping the run.
 *
 * <p>Tenant-active (multi-tenancy v2): every INSERT point ({@code AutonomousRunService}) stamps the
 * tenant explicitly from the parent run - deliberately no {@code TenantBaseListener}, whose
 * thread-local default would silently land orchestrator-callback writes in the default tenant.
 */
@Getter
@Setter
@Entity
@Table(name = "autonomous_directives")
public class AutonomousDirective implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "autonomous_directive_id")
  @JsonProperty("autonomous_directive_id")
  @Schema(description = "ID of the directive")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "autonomous_directive_run_id", nullable = false)
  @JsonProperty("autonomous_directive_run_id")
  @Schema(description = "Autonomous run this directive steers")
  private String runId;

  @NotNull
  @Column(name = "autonomous_directive_content", nullable = false, columnDefinition = "text")
  @JsonProperty("autonomous_directive_content")
  @Schema(description = "Free-text steering instruction")
  private String content;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "autonomous_directive_status", nullable = false)
  @JsonProperty("autonomous_directive_status")
  @Schema(description = "Whether the orchestrator has consumed the directive")
  private AutonomousDirectiveStatus status = AutonomousDirectiveStatus.PENDING;

  @CreationTimestamp
  @Column(name = "autonomous_directive_created_at", updatable = false)
  @JsonProperty("autonomous_directive_created_at")
  @Schema(description = "Creation date")
  private Instant createdAt;

  @Column(name = "autonomous_directive_consumed_at")
  @JsonProperty("autonomous_directive_consumed_at")
  @Schema(description = "When the orchestrator consumed the directive")
  private Instant consumedAt;
}
