package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "tag_rules")
@EntityListeners(ModelBaseListener.class)
public class TagRule implements Base {

  @Id
  @Column(name = "tag_rule_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("tag_rule_id")
  @NotBlank
  @Queryable(searchable = true)
  private String id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "tag_id")
  @JsonProperty("tag_rule_tag")
  @Queryable(searchable = true, filterable = true, path = "tag.name")
  private Tag tag;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "tag_rule_asset_groups",
      joinColumns = @JoinColumn(name = "tag_rule_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_group_id"))
  @Queryable(filterable = true, path = "assetGroups.name")
  @JsonProperty("tag_rule_asset_groups")
  private List<AssetGroup> assetGroups = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.TAG_RULE;

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
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
