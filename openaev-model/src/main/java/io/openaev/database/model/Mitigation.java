package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdListSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "mitigations")
@EntityListeners(ModelBaseListener.class)
public class Mitigation implements Base {

  @Id
  @Column(name = "mitigation_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("mitigation_id")
  @NotBlank
  private String id;

  @Column(name = "mitigation_stix_id")
  @JsonProperty("mitigation_stix_id")
  @NotBlank
  private String stixId;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "mitigation_name")
  @JsonProperty("mitigation_name")
  @NotBlank
  private String name;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "mitigation_description")
  @JsonProperty("mitigation_description")
  private String description;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "mitigation_external_id")
  @JsonProperty("mitigation_external_id")
  @NotBlank
  private String externalId;

  @Type(StringArrayType.class)
  @Column(name = "mitigation_log_sources", columnDefinition = "text[]")
  @JsonProperty("mitigation_log_sources")
  private String[] logSources = new String[0];

  @Column(name = "mitigation_threat_hunting_techniques", columnDefinition = "text")
  @JsonProperty("mitigation_threat_hunting_techniques")
  private String threatHuntingTechniques;

  @Queryable(sortable = true)
  @Column(name = "mitigation_created_at")
  @JsonProperty("mitigation_created_at")
  @NotNull
  private Instant createdAt = now();

  @Queryable(sortable = true)
  @Column(name = "mitigation_updated_at")
  @JsonProperty("mitigation_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "mitigations_attack_patterns",
      joinColumns = @JoinColumn(name = "mitigation_id"),
      inverseJoinColumns = @JoinColumn(name = "attack_pattern_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("mitigation_attack_patterns")
  private List<AttackPattern> attackPatterns = new ArrayList<>();
}
