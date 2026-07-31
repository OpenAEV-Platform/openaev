package io.openaev.database.model.attackpath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

/**
 * One row = one collector-result detail line for one attack-path execution. Snapshot-only data used
 * by execution detail views so collector rows remain stable over time.
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_execution_collector")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantBaseListener.class)
public class AttackPathExecutionCollector implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "attackpath_execution_collector_id")
  private String id;

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  private Tenant tenant;

  @Column(name = "attackpath_execution_collector_simulation_id", nullable = false)
  private String simulationId;

  @Column(name = "attackpath_execution_id", nullable = false)
  private String executionId;

  @Column(name = "attackpath_execution_collector_expectation_type", nullable = false)
  private String expectationType;

  @Column(name = "attackpath_execution_collector_source_id")
  private String sourceId;

  @Column(name = "attackpath_execution_collector_source_type")
  private String sourceType;

  @Column(name = "attackpath_execution_collector_source_name")
  private String sourceName;

  @Column(name = "attackpath_execution_collector_source_asset_id")
  private String sourceAssetId;

  @Column(name = "attackpath_execution_collector_result_status_label", nullable = false)
  private String resultStatusLabel;

  @Column(name = "attackpath_execution_collector_detection_time")
  private String detectionTime;

  @Type(JsonType.class)
  @Column(name = "attackpath_execution_collector_alerts", columnDefinition = "jsonb")
  private JsonNode alerts;

  @Column(name = "attackpath_execution_collector_result_score")
  private Double resultScore;

  @Column(name = "attackpath_execution_collector_result_date")
  private String resultDate;
}
