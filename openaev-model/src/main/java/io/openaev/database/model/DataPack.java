package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "datapacks")
public class DataPack {
  @Id
  @Column(name = "datapack_id", updatable = false, nullable = false)
  @JsonProperty("datapack_id")
  @NotBlank
  private String id;
}
