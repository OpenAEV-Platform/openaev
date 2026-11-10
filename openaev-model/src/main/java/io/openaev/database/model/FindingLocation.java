package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "finding_locations")
public class FindingLocation implements Base {

  @Id
  @Column(name = "finding_location_id")
  @JsonProperty("finding_location_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_location_asset_id", updatable = false)
  @JsonIgnore
  private Asset asset;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_location_user_id", updatable = false)
  @JsonIgnore
  private User user;

  // Computed by the database from the asset and the user: asset:<id>, user:<id> or unknown
  @Generated
  @Column(name = "finding_location_key", insertable = false, updatable = false)
  @JsonProperty("finding_location_key")
  private String locationKey;

  @Override
  public boolean isListened() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FindingLocation other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
