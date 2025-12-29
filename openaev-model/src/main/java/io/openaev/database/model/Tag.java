package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.jsonapi.BusinessId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "tags")
@EntityListeners(ModelBaseListener.class)
public class Tag implements Base {

  @Setter
  @Id
  @Column(name = "tag_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("tag_id")
  @NotBlank
  @Schema(description = "ID of the tag")
  private String id;

  @Getter
  @Column(name = "tag_name")
  @JsonProperty("tag_name")
  @Queryable(searchable = true, sortable = true)
  @NotBlank
  @Schema(description = "Name of the tag")
  @BusinessId
  private String name;

  @Getter
  @Column(name = "tag_color")
  @JsonProperty("tag_color")
  @Queryable(sortable = true)
  @Schema(description = "Color of the tag")
  private String color;

  @Getter
  @Column(name = "tag_created_at")
  @JsonProperty("tag_created_at")
  @JsonIgnore
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Setter
  @Column(name = "tag_updated_at")
  @JsonProperty("tag_updated_at")
  @JsonIgnore
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.TAG;

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return true;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setName(String name) {
    this.name = name.toLowerCase();
  }

  public void setColor(String color) {
    this.color = color.toLowerCase();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Tag other = (Tag) o;
    if (StringUtils.isBlank(id) || StringUtils.isBlank(other.getId())) {
      return !StringUtils.isBlank(name) && name.equals(other.getName());
    }
    return id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
