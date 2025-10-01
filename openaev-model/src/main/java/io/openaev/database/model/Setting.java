package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "parameters")
@EntityListeners(ModelBaseListener.class)
@NoArgsConstructor
public class Setting implements Base {

  @Id
  @Column(name = "parameter_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("setting_id")
  private String id;

  @Column(name = "parameter_key")
  @JsonProperty("setting_key")
  private String key;

  @Column(name = "parameter_value")
  @JsonProperty("setting_value")
  private String value;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PLATFORM_SETTING;

  public Setting(String key, String value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

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
