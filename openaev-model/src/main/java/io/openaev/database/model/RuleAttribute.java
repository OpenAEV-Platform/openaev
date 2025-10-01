package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "rule_attributes")
@EntityListeners(ModelBaseListener.class)
public class RuleAttribute implements Base {

  @Id
  @Column(name = "attribute_id")
  @JsonProperty("rule_attribute_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private UUID id;

  @Column(name = "attribute_name")
  @JsonProperty("rule_attribute_name")
  @NotBlank
  private String name;

  @Column(name = "attribute_columns")
  @JsonProperty("rule_attribute_columns")
  private String columns;

  @Column(name = "attribute_default_value")
  @JsonProperty("rule_attribute_default_value")
  private String defaultValue;

  @Type(PostgreSQLHStoreType.class)
  @Column(name = "attribute_additional_config")
  @JsonProperty("rule_attribute_additional_config")
  private Map<String, String> additionalConfig;

  @CreationTimestamp
  @Column(name = "attribute_created_at")
  @JsonProperty("rule_attribute_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "attribute_updated_at")
  @JsonProperty("rule_attribute_updated_at")
  private Instant updateDate;

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

  @Override
  public String getId() {
    return this.id != null ? this.id.toString() : "";
  }

  @Override
  public void setId(String id) {
    this.id = UUID.fromString(id);
  }
}
