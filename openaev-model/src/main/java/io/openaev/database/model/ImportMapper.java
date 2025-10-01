package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
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
@Table(name = "import_mappers")
@EntityListeners(ModelBaseListener.class)
public class ImportMapper implements Base {

  @Id
  @Column(name = "mapper_id")
  @JsonProperty("import_mapper_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private UUID id;

  @Queryable(sortable = true)
  @Column(name = "mapper_name")
  @JsonProperty("import_mapper_name")
  @NotBlank
  private String name;

  @Column(name = "mapper_inject_type_column")
  @JsonProperty("import_mapper_inject_type_column")
  @NotNull
  private String injectTypeColumn;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "importer_mapper_id", nullable = false)
  @JsonProperty("import_mapper_inject_importers")
  private List<InjectImporter> injectImporters = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "mapper_created_at")
  @JsonProperty("import_mapper_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "mapper_updated_at")
  @JsonProperty("import_mapper_updated_at")
  private Instant updateDate;

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

  @Override
  public String getId() {
    return this.id != null ? this.id.toString() : "";
  }

  @Override
  public void setId(String id) {
    this.id = UUID.fromString(id);
  }
}
