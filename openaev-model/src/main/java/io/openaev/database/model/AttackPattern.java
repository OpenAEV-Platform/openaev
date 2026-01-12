package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.jsonapi.BusinessId;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "attack_patterns")
@EntityListeners(ModelBaseListener.class)
public class AttackPattern implements Base {

  @Id
  @Column(name = "attack_pattern_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("attack_pattern_id")
  @NotBlank
  private String id;

  @Column(name = "attack_pattern_stix_id")
  @JsonProperty("attack_pattern_stix_id")
  @NotBlank
  private String stixId;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "attack_pattern_name")
  @JsonProperty("attack_pattern_name")
  @NotBlank
  private String name;

  @Queryable(searchable = true)
  @Column(name = "attack_pattern_description")
  @JsonProperty("attack_pattern_description")
  private String description;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "attack_pattern_external_id")
  @JsonProperty("attack_pattern_external_id")
  @NotBlank
  @BusinessId
  private String externalId;

  @Type(StringArrayType.class)
  @Column(name = "attack_pattern_platforms", columnDefinition = "text[]")
  @JsonProperty("attack_pattern_platforms")
  private String[] platforms = new String[0];

  @Type(StringArrayType.class)
  @Column(name = "attack_pattern_permissions_required", columnDefinition = "text[]")
  @JsonProperty("attack_pattern_permissions_required")
  private String[] permissionsRequired = new String[0];

  @Queryable(sortable = true)
  @Column(name = "attack_pattern_created_at")
  @JsonProperty("attack_pattern_created_at")
  @CreationTimestamp
  private Instant createdAt = now();

  @Queryable(sortable = true)
  @Column(name = "attack_pattern_updated_at")
  @JsonProperty("attack_pattern_updated_at")
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attack_pattern_parent")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_pattern_parent")
  @Schema(type = "string")
  private AttackPattern parent;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "attack_patterns_kill_chain_phases",
      joinColumns = @JoinColumn(name = "attack_pattern_id"),
      inverseJoinColumns = @JoinColumn(name = "phase_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("attack_pattern_kill_chain_phases")
  private List<KillChainPhase> killChainPhases = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.ATTACK_PATTERN;

  // UpdatedAt now used to sync with linked object
  public void setKillChainPhases(List<KillChainPhase> killChainPhases) {
    this.updatedAt = now();
    this.killChainPhases = killChainPhases;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
