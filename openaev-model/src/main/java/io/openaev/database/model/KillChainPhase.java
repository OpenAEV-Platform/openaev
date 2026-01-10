package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.jsonapi.BusinessId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "kill_chain_phases")
@EntityListeners(ModelBaseListener.class)
public class KillChainPhase implements Base {

  @Id
  @Column(name = "phase_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("phase_id")
  @NotBlank
  private String id;

  @Column(name = "phase_external_id")
  @JsonProperty("phase_external_id")
  @NotBlank
  private String externalId;

  @Column(name = "phase_stix_id")
  @JsonProperty("phase_stix_id")
  private String stixId;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "phase_name")
  @JsonProperty("phase_name")
  @NotBlank
  @BusinessId
  private String name;

  @Column(name = "phase_shortname")
  @JsonProperty("phase_shortname")
  @NotBlank
  private String shortName;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "phase_kill_chain_name")
  @JsonProperty("phase_kill_chain_name")
  @NotBlank
  @BusinessId
  private String killChainName;

  @Column(name = "phase_description")
  @JsonProperty("phase_description")
  private String description;

  @Queryable(sortable = true)
  @Column(name = "phase_order")
  @JsonProperty("phase_order")
  private Long order = 0L;

  @Queryable(sortable = true)
  @Column(name = "phase_created_at")
  @JsonProperty("phase_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "phase_updated_at")
  @JsonProperty("phase_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.KILL_CHAIN_PHASE;

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
