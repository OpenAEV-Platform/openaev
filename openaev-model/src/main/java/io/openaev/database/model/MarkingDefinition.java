package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A marking definition — one level of one classification scale, e.g. {@code TLP:RED}.
 *
 * <p>Markings are the vocabulary a <i>clearance</i> is expressed in. A group is granted a set of
 * markings ({@code groups_markings}); a row is attached a set of markings ({@code marking_ids});
 * the row is visible when its set is contained in the reader's clearance.
 *
 * <p>{@link #type} groups markings into independent scales (TLP, PAP, or a tenant's own), and
 * {@link #order} ranks them within a scale. Holding a level implies holding every lower level of
 * the <b>same</b> scale, and scales never imply one another — a TLP clearance says nothing about
 * PAP.
 *
 * <p>Tenant-scoped on <b>v2</b> (statement inspector + {@code can_access_tenant}); there is
 * deliberately no Hibernate {@code @Filter}. The table is also never marking-filtered: it is what a
 * clearance is resolved <i>from</i>, so filtering it would make resolution depend on its own
 * result.
 */
@Entity
@Table(name = "marking_definitions")
@EntityListeners({ModelBaseListener.class})
public class MarkingDefinition implements TenantBase {

  public static final String TYPE_TLP = "TLP";
  public static final String TYPE_PAP = "PAP";

  @Setter
  @Id
  @Column(name = "marking_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("marking_id")
  @NotBlank
  @Schema(description = "Unique identifier of the marking definition")
  private String id;

  @Getter
  @Setter
  @Column(name = "marking_type")
  @JsonProperty("marking_type")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  @Schema(description = "Classification scale this marking belongs to, e.g. TLP or PAP")
  private String type;

  @Getter
  @Setter
  @Column(name = "marking_name")
  @JsonProperty("marking_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  @Schema(description = "Name of the marking, unique within the tenant, e.g. TLP:RED")
  private String name;

  @Getter
  @Setter
  @Column(name = "marking_order")
  @JsonProperty("marking_order")
  @Queryable(sortable = true)
  @NotNull
  @Schema(
      description =
          "Rank within the scale — higher is more restrictive. Holding a level implies holding"
              + " every lower level of the same scale.")
  private Integer order;

  @Getter
  @Setter
  @Column(name = "marking_color")
  @JsonProperty("marking_color")
  @Schema(description = "Display colour, as a hex code")
  private String color;

  @Getter
  @Column(name = "marking_created_at")
  @JsonProperty("marking_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "marking_updated_at")
  @JsonProperty("marking_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  @Getter
  @Setter
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.MARKING_DEFINITION;

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return true;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    return id != null && id.equals(((Base) o).getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
