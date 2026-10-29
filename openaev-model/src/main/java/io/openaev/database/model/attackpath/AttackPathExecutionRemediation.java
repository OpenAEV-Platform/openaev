package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Snapshot of payload remediations frozen at step execution time for attack-path execution detail.
 *
 * <p>This entity is fully switched to v2 tenant isolation (statement inspector + {@code
 * can_access_tenant}). Keep the v1 {@code @Filter} and {@code TenantBaseListener} removed to avoid
 * mixed isolation/write-attribution modes; every write stamps {@code tenant} explicitly from the
 * inject it snapshots (see {@code AttackPathExecutionIngestionService}).
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_execution_remediation")
public class AttackPathExecutionRemediation implements TenantBase {

  @Id
  @Column(name = "attackpath_execution_remediation_id")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "attackpath_execution_remediation_step_id", nullable = false)
  private String stepId;

  @Column(
      name = "attackpath_execution_remediation_values",
      nullable = false,
      columnDefinition = "TEXT")
  private String values;

  @Column(name = "attackpath_execution_remediation_author_rule", nullable = false)
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private DetectionRemediation.AUTHOR_RULE authorRule;

  @Column(name = "attackpath_execution_remediation_collector_type")
  private String collectorType;

  @Column(name = "attackpath_execution_remediation_security_platform", nullable = false)
  private String securityPlatformId;
}
