package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "inject_importers")
@EntityListeners(ModelBaseListener.class)
public class InjectImporter implements Base {

  @Id
  @Column(name = "importer_id")
  @JsonProperty("inject_importer_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private UUID id;

  @Column(name = "importer_import_type_value")
  @JsonProperty("inject_importer_type_value")
  @NotBlank
  private String importTypeValue;

  @OneToOne
  @JoinColumn(name = "importer_injector_contract_id")
  @JsonProperty("inject_importer_injector_contract")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @NotNull
  @Schema(type = "string")
  private InjectorContract injectorContract;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "attribute_inject_importer_id", nullable = false)
  @JsonProperty("inject_importer_rule_attributes")
  private List<RuleAttribute> ruleAttributes = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "importer_created_at")
  @JsonProperty("inject_importer_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "importer_updated_at")
  @JsonProperty("inject_importer_updated_at")
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
