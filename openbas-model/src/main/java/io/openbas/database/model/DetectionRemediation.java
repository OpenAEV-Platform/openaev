package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "detection_remediations")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DetectionRemediation implements Base {

  @Id
  @Column(name = "detection_remediation_id")
  @JsonProperty("detection_remediation_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "detection_remediation_payload_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("detection_remediation_payload_id")
  @Schema(type = "string")
  @NotNull
  private Payload payload;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "detection_remediation_collector_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("detection_remediation_collector_id")
  @Schema(type = "string")
  @NotNull
  private Collector collector;

  @Column(name = "detection_remediation_values", columnDefinition = "JSONB")
  @JsonProperty("detection_remediation_values")
  @NotNull
  private String values;

  // -- AUDIT --

  @CreationTimestamp
  @Queryable(filterable = true, sortable = true, label = "created at")
  @Column(name = "detection_remediation_created_at", updatable = false)
  @JsonProperty("detection_remediation_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Queryable(filterable = true, sortable = true, label = "updated at")
  @Column(name = "detection_remediation_updated_at")
  @JsonProperty("detection_remediation_updated_at")
  private Instant updateDate;
}
